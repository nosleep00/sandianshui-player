package com.sandianshui.player.core.remote

import com.sandianshui.player.core.subtitle.SubtitleFormat
import com.sandianshui.player.core.subtitle.SubtitleMatcher
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** ASMR.one 搜索到的一部作品（音声）。 */
data class AsmrWork(
    val id: Int,
    val title: String,
    val circle: String,
    val vas: List<String>,
    /** 总时长（秒）；未知为 null。 */
    val durationSec: Int?,
    val dlCount: Int,
    val coverUrl: String?,
)

/**
 * 作品内的一条可播放音轨（流式地址）。
 * 若该作品在同文件夹内带同名字幕 / 歌词，则 [subtitleUrl] 与 [subtitleFormat] 非空。
 */
data class AsmrTrack(
    val title: String,
    val streamUrl: String,
    /** 该音轨时长（秒）；未知为 null。 */
    val durationSec: Double?,
    /** 同名匹配到的字幕 / 歌词文本流地址；无则 null。 */
    val subtitleUrl: String? = null,
    val subtitleFormat: SubtitleFormat? = null,
)

/**
 * 作品内的一个字幕 / 歌词文件（跨整个作品树收集，用于深度检索）。
 */
data class RemoteSubtitle(
    val fileName: String,
    val format: SubtitleFormat,
    val url: String,
)

/** 搜索结果默认每页条数。 */
const val DEFAULT_PAGE_SIZE: Int = 20

/**
 * 一次搜索（某一页）的结果：当前页作品 + 服务端分页信息。
 */
data class AsmrSearchResult(
    val works: List<AsmrWork>,
    val currentPage: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
)

/**
 * 在线音源 JSON 接口解析器。
 *
 * 应用不内置接口地址，[baseUrl] 由用户在「在线音源设置」中填写（见 [RemoteSourceStore]）。
 * 部分网络环境需通过代理访问。
 */
object AsmrOne {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    /**
     * 关键词搜索（阻塞，应在 IO 线程调用）。
     * 默认按**销售数量（dl_count）降序**，每页 [DEFAULT_PAGE_SIZE]=20 条。
     * @param baseUrl 用户配置的接口根地址。
     * @param page 从 1 开始的页码。
     */
    fun search(
        keyword: String,
        baseUrl: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): AsmrSearchResult {
        val q = keyword.trim()
        val safePage = page.coerceAtLeast(1)
        if (q.isEmpty() || baseUrl.isBlank()) {
            return AsmrSearchResult(emptyList(), safePage, pageSize, 0, 0)
        }
        val url = buildString {
            append(baseUrl).append("/api/search/").append(enc(q))
            append("?order=dl_count&sort=desc")
            append("&page=").append(safePage)
            append("&pageSize=").append(pageSize.coerceIn(1, 50))
            append("&subtitle=0&includeTranslationWorks=true")
        }
        val (code, body) = SimpleHttp.get(url, UA)
        if (code !in 200..299 || body.isBlank()) {
            return AsmrSearchResult(emptyList(), safePage, pageSize, 0, 0)
        }
        return parseSearchResult(body)
    }

    /**
     * 获取某作品的可播放音轨列表（阻塞，应在 IO 线程调用）。
     * 优先取 mp3 文件夹（体积小、适合流式），按文件名排序；无 mp3 时回退到 wav。
     */
    fun tracks(workId: Int, baseUrl: String): List<AsmrTrack> {
        if (baseUrl.isBlank()) return emptyList()
        val url = "$baseUrl/api/tracks/$workId"
        val (code, body) = SimpleHttp.get(url, UA)
        if (code !in 200..299 || body.isBlank()) return emptyList()
        return parseTracks(body)
    }

    /**
     * 深度检索用：获取某作品**整棵文件树**内的全部字幕 / 歌词文件（跨文件夹），
     * 供播放时按当前音轨名模糊匹配。阻塞，应在 IO 线程调用。
     */
    fun subtitleCandidates(workId: Int, baseUrl: String): List<RemoteSubtitle> {
        if (baseUrl.isBlank()) return emptyList()
        val url = "$baseUrl/api/tracks/$workId"
        val (code, body) = SimpleHttp.get(url, UA)
        if (code !in 200..299 || body.isBlank()) return emptyList()
        return parseSubtitleCandidates(body)
    }

    /**
     * 解析作品树，递归收集所有 type=text 且为字幕 / 歌词格式的节点。
     * 纯函数，可单测。
     */
    fun parseSubtitleCandidates(json: String): List<RemoteSubtitle> {
        val out = ArrayList<RemoteSubtitle>()
        collectTextRecursive(JSONArray(json), out)
        return out
    }

    private fun collectTextRecursive(children: JSONArray, out: MutableList<RemoteSubtitle>) {
        for (i in 0 until children.length()) {
            val node = children.optJSONObject(i) ?: continue
            when (node.optString("type")) {
                "folder" -> {
                    val ch = node.optJSONArray("children") ?: continue
                    collectTextRecursive(ch, out)
                }
                "text" -> {
                    val name = node.optString("title")
                    val fmt = SubtitleFormat.fromFileName(name) ?: continue
                    val url = node.optString("mediaStreamUrl")
                        .ifBlank { node.optString("mediaDownloadUrl") }
                    if (url.isNotBlank()) out.add(RemoteSubtitle(name, fmt, url))
                }
            }
        }
    }

    /**
     * 解析音轨响应：顶层为文件夹数组，其中既可能有子文件夹，也可能直接挂音频 / 文本。
     * 遍历**整棵树**收集所有音频（根目录 + 各子文件夹），并在每个文件夹内按同名
     * 匹配字幕（SRT / VTT）/ 歌词（LRC）。纯函数，可单测。
     */
    fun parseTracks(json: String): List<AsmrTrack> {
        val out = ArrayList<AsmrTrack>()
        processFolder(JSONArray(json), out)
        out.sortBy { it.title }
        return out
    }

    /**
     * 处理一个文件夹的 children（顶层根数组即视为根文件夹的 children）：
     * 收集该文件夹直属音频并用 [SubtitleMatcher] 同文件夹同名匹配，再递归子文件夹。
     */
    private fun processFolder(children: JSONArray, out: MutableList<AsmrTrack>) {
        val subfolders = ArrayList<JSONObject>()
        val audios = ArrayList<JSONObject>()
        val textNames = ArrayList<String>()
        val textNodes = HashMap<String, JSONObject>()

        for (i in 0 until children.length()) {
            val node = children.optJSONObject(i) ?: continue
            when (node.optString("type")) {
                "folder" -> subfolders.add(node)
                "audio" -> audios.add(node)
                "text" -> {
                    // 只收集真正的字幕 / 歌词文件（说明性 txt 由格式判定排除）
                    val name = node.optString("title")
                    if (SubtitleFormat.fromFileName(name) != null) {
                        textNames.add(name)
                        textNodes[name] = node
                    }
                }
            }
        }

        for (audio in audios) {
            val audioName = audio.optString("title").ifBlank { "未命名" }
            val audioUrl = audio.optString("mediaStreamUrl")
                .ifBlank { audio.optString("mediaDownloadUrl") }
            if (audioUrl.isBlank()) continue

            // 去掉音频扩展名作为同名基准
            val base = audioName.substringBeforeLast('.', audioName)
            val match = SubtitleMatcher.match(base, textNames)
            val subtitleUrl = match?.let { m ->
                textNodes[m.fileName]?.let { tn ->
                    tn.optString("mediaStreamUrl")
                        .ifBlank { tn.optString("mediaDownloadUrl") }
                        .takeIf { it.isNotBlank() }
                }
            }
            val dur = if (audio.isNull("duration")) null
            else audio.optDouble("duration", -1.0).takeIf { it >= 0 }
            out.add(
                AsmrTrack(
                    title = audioName,
                    streamUrl = audioUrl,
                    durationSec = dur,
                    subtitleUrl = subtitleUrl,
                    subtitleFormat = match?.format,
                )
            )
        }

        for (folder in subfolders) {
            val ch = folder.optJSONArray("children") ?: continue
            processFolder(ch, out)
        }
    }

    /** 解析搜索响应：顶层 { works: [...], pagination }。纯函数，可单测。 */
    fun parse(json: String): List<AsmrWork> = parseSearchResult(json).works

    /**
     * 解析完整搜索响应（作品列表 + 分页信息）。纯函数，可单测。
     * pagination 缺字段时给出兜底值，保证翻页按钮可用。
     */
    fun parseSearchResult(json: String): AsmrSearchResult {
        val root = JSONObject(json)
        val worksArr = root.optJSONArray("works")
        val works = ArrayList<AsmrWork>()
        if (worksArr != null) {
            for (i in 0 until worksArr.length()) works.add(parseWork(worksArr.getJSONObject(i)))
        }

        val pg = root.optJSONObject("pagination")
        val currentPage = pg?.optInt("currentPage", 1)?.takeIf { it >= 1 } ?: 1
        val pageSize = pg?.optInt("pageSize", DEFAULT_PAGE_SIZE)?.takeIf { it >= 1 }
            ?: DEFAULT_PAGE_SIZE
        // totalCount 缺失时退化为当前页条数
        val totalCount = pg?.optInt("totalCount", works.size)?.takeIf { it >= 0 } ?: works.size
        // totalPages 优先取服务端值；缺失时按 totalCount/pageSize 上取整
        val totalPages = pg?.optInt("totalPages", -1)?.takeIf { it >= 1 }
            ?: if (totalCount == 0) 0
            else (totalCount + pageSize - 1) / pageSize

        return AsmrSearchResult(
            works = works,
            currentPage = currentPage,
            pageSize = pageSize,
            totalCount = totalCount,
            totalPages = totalPages,
        )
    }

    private fun parseWork(o: JSONObject): AsmrWork {
        val vaArr = o.optJSONArray("vas")
        val vas = ArrayList<String>()
        if (vaArr != null) {
            for (i in 0 until vaArr.length()) {
                val n = vaArr.getJSONObject(i).optString("name")
                if (n.isNotBlank()) vas.add(n)
            }
        }
        val duration = if (o.isNull("duration")) null else o.optInt("duration", -1).takeIf { it >= 0 }
        return AsmrWork(
            id = o.optInt("id"),
            title = o.optString("title").ifBlank { "未命名" },
            circle = o.optJSONObject("circle")?.optString("name").orEmpty(),
            vas = vas,
            durationSec = duration,
            dlCount = o.optInt("dl_count", 0),
            coverUrl = o.optString("mainCoverUrl").takeIf { it.isNotBlank() },
        )
    }
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

