package com.sandianshui.player.core.subtitle

/**
 * 同目录同名匹配结果。
 * @param fileName 匹配到的文件名（含扩展名）
 */
data class SubtitleMatch(
    val fileName: String,
    val format: SubtitleFormat,
)

/**
 * 按音频同名规则在同文件夹内匹配字幕 / 歌词。
 *
 * 文件名规则（在音频同名基础上允许再带「变体后缀」）：
 *  - 精确同名：`音频名.srt`
 *  - 带变体：`音频名.zh.srt`、`音频名.zh-CN.srt`、`音频名.16k.srt` ……
 *    即「音频名 + 句点 + 变体标签 + .扩展名」。仅前缀相同但没有句点分隔的
 *    （如音频名 `song` 对应 `song2.srt`）不算匹配，避免串到别的音频。
 *
 * 优先级：
 *  1. 格式：SRT -> VTT -> LRC（字幕永远优先于歌词）；
 *  2. 同一格式内：精确同名 > 中文变体（.zh/.zh-CN/.cn 等）> 其他变体（.16k/.en 等）；
 *  3. 仍并列时按文件名升序，保证结果确定。
 *
 * 该对象为纯逻辑、不依赖 Android，便于单元测试。
 */
object SubtitleMatcher {

    /** 常见中文语言标签（小写）；此外任何以 zh 开头的标签也视为中文。 */
    private val CHINESE_TAGS = setOf(
        "zh", "cn", "chs", "cht", "sc", "tc", "chinese",
        "zh-cn", "zh-hans", "zh-hant", "zh-sg", "zh-tw", "zh-hk",
    )

    /**
     * @param baseName 音频文件名去掉扩展名后的部分，如「静夜思」
     * @param fileNames 同一文件夹下的全部文件名
     */
    fun match(baseName: String, fileNames: Collection<String>): SubtitleMatch? {
        best(fileNames, baseName, SubtitleFormat.SRT)?.let { return it }
        best(fileNames, baseName, SubtitleFormat.VTT)?.let { return it }
        best(fileNames, baseName, SubtitleFormat.LRC)?.let { return it }
        return null
    }

    /**
     * 深度匹配：先用 [match] 的严格规则（同名 / 同名+变体），找不到时退化为**模糊包含**：
     * 字幕主干包含音频名，或音频名包含字幕主干（要求较短一方至少 3 个字符，避免
     * `a.srt` 这种过短名字误匹配）。仍按 SRT -> VTT -> LRC 优先级，同格式内按
     * 「包含方向 + 文件名升序」排序，保证结果确定。
     *
     * 用于「深度检索」：递归收集整个文件夹 / 作品树的全部字幕后，为当前曲目
     * 跨目录找最可能的那一个。
     */
    fun matchDeep(baseName: String, fileNames: Collection<String>): SubtitleMatch? {
        match(baseName, fileNames)?.let { return it }
        fuzzyBest(fileNames, baseName, SubtitleFormat.SRT)?.let { return it }
        fuzzyBest(fileNames, baseName, SubtitleFormat.VTT)?.let { return it }
        fuzzyBest(fileNames, baseName, SubtitleFormat.LRC)?.let { return it }
        return null
    }

    /** 与 [matchDeep] 同义，但返回全部候选（按优先级排序），便于界面展示找到了哪些。 */
    fun matchCandidates(baseName: String, fileNames: Collection<String>): List<SubtitleMatch> {
        val out = ArrayList<SubtitleMatch>()
        for (fmt in listOf(SubtitleFormat.SRT, SubtitleFormat.VTT, SubtitleFormat.LRC)) {
            best(fileNames, baseName, fmt)?.let { out.add(it) }
        }
        if (out.isNotEmpty()) return out
        for (fmt in listOf(SubtitleFormat.SRT, SubtitleFormat.VTT, SubtitleFormat.LRC)) {
            fuzzyBest(fileNames, baseName, fmt)?.let { out.add(it) }
        }
        return out
    }

    /** 在指定格式的候选文件中，按变体优先级挑出最合适的一个。 */
    private fun best(
        fileNames: Collection<String>,
        baseName: String,
        format: SubtitleFormat,
    ): SubtitleMatch? = fileNames
        .mapNotNull { name ->
            val rank = variantRank(name, baseName, format.ext) ?: return@mapNotNull null
            Scored(name, rank)
        }
        .sortedWith(compareBy({ it.rank }, { it.fileName.lowercase() }))
        .firstOrNull()
        ?.let { SubtitleMatch(it.fileName, format) }

    /**
     * 判定文件名是否为该音频、该格式的字幕，并返回变体优先级（越小越优先）。
     * 不匹配返回 null。兼容「另存为 .txt」：`xxx.srt.txt` 视为 `xxx.srt`。
     */
    private fun variantRank(fileName: String, baseName: String, ext: String): Int? {
        var lower = fileName.lowercase()
        // 去掉浏览器 / 记事本另存时多出来的末尾 .txt
        if (lower.endsWith(".txt")) lower = lower.removeSuffix(".txt")
        val dotExt = ".$ext"
        if (!lower.endsWith(dotExt)) return null
        val stem = lower.removeSuffix(dotExt)
        val base = baseName.lowercase()
        return when {
            stem == base -> 0 // 精确同名
            stem.startsWith("$base.") -> {
                val variant = stem.substring(base.length + 1)
                if (isChineseVariant(variant)) 1 else 2 // 中文变体优先于其他变体
            }
            else -> null
        }
    }

    private fun isChineseVariant(variant: String): Boolean {
        if (variant.isBlank()) return false
        val lang = variant.substringBefore('.').trim()
        return lang in CHINESE_TAGS || lang.startsWith("zh")
    }

    /**
     * 模糊匹配：在指定格式的候选中，挑出主干与音频名互相包含的最佳一个。
     * 优先级：字幕主干包含音频名（更常见，如 `歌曲_完整版.srt` 包含 `歌曲`）>
     * 音频名包含字幕主干；同级按文件名升序。
     */
    private fun fuzzyBest(
        fileNames: Collection<String>,
        baseName: String,
        format: SubtitleFormat,
    ): SubtitleMatch? {
        val base = baseName.lowercase()
        data class Fuzzy(val fileName: String, val rank: Int)
        val candidates = fileNames.mapNotNull { name ->
            var lower = name.lowercase()
            if (lower.endsWith(".txt")) lower = lower.removeSuffix(".txt")
            val dotExt = ".${format.ext}"
            if (!lower.endsWith(dotExt)) return@mapNotNull null
            val stem = lower.removeSuffix(dotExt)
            // 较短一方至少 2 个字符，避免「a.srt」这种单字符名字误匹配；
            // 中文 2 字（如「歌曲」）是有意义的名字，应允许参与模糊匹配。
            val shorter = minOf(stem.length, base.length)
            when {
                stem == base -> null // 精确同名已由 strict 处理，这里不重复
                shorter >= 2 && stem.contains(base) ->
                    Fuzzy(name, 0)
                shorter >= 2 && base.contains(stem) ->
                    Fuzzy(name, 1)
                else -> null
            }
        }
        return candidates
            .sortedWith(compareBy({ it.rank }, { it.fileName.lowercase() }))
            .firstOrNull()
            ?.let { SubtitleMatch(it.fileName, format) }
    }

    private data class Scored(val fileName: String, val rank: Int)
}
