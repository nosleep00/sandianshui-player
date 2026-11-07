package com.sandianshui.player.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.sandianshui.player.core.subtitle.SubtitleFormat
import com.sandianshui.player.core.subtitle.SubtitleMatch
import com.sandianshui.player.core.subtitle.SubtitleMatcher

/**
 * 基于 SAF 的媒体扫描：
 * - [scan]：「选择文件夹」(OpenDocumentTree)，可枚举整个文件夹并自动匹配；
 * - [scanSingleAudio]：「选择单个音频文件」(OpenDocument)，只加载用户选中的那一首，
 *   并尽力推导同目录同名配套文件。
 */
object FolderScanner {

    private val AUDIO_EXT = setOf("mp3", "wav")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")

    /**
     * 扫描文件夹，返回其中的音频列表（含自动匹配结果），按文件名排序。
     * 拿不到文件夹访问权时返回空列表。
     *
     * 支持两种目录布局：
     * 1. 平铺：音频与字幕 / 封面都在所选文件夹内（同名匹配）；
     * 2. 分文件夹：所选文件夹下有按类别命名的子文件夹（如 `audio/` 与 `subtitle/`、
     *    `音乐/` 与 `字幕/`），此时跨子文件夹按同名一一配对，字幕仍优先于歌词。
     */
    fun scan(context: Context, treeUri: Uri): List<MediaTrack> {
        val resolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val children = root.listFiles()
        val folders = children.filter { it.isDirectory }
        val rootFiles = children.filter { it.isFile }

        // 每个子文件夹及其直属文件
        val folderFiles: List<Pair<DocumentFile, List<DocumentFile>>> =
            folders.map { it to immediateFiles(it) }

        // 子文件夹按「名称关键字 + 实际内容」双重判定：名称不标准也能靠内容识别
        val audioFolders = folderFiles.filter { (f, files) ->
            FolderCategories.categoryOf(f.name) == FolderCategory.AUDIO ||
                files.any { extOf(it.name) in AUDIO_EXT }
        }
        val textFolders = folderFiles.filter { (f, files) ->
            val c = FolderCategories.categoryOf(f.name)
            c == FolderCategory.SUBTITLE || c == FolderCategory.LYRICS ||
                files.any { SubtitleFormat.fromFileName(it.name) != null }
        }
        val imageFolders = folderFiles.filter { (f, files) ->
            FolderCategories.categoryOf(f.name) == FolderCategory.IMAGE ||
                files.any { extOf(it.name) in IMAGE_EXT }
        }

        // 识别出任一类「分类子文件夹」即按分文件夹布局处理
        val splitLayout =
            audioFolders.isNotEmpty() || textFolders.isNotEmpty() || imageFolders.isNotEmpty()

        return if (splitLayout) {
            val audioFiles = audioFolders.flatMap { it.second }.filterAudio()
                .ifEmpty { rootFiles.filterAudio() }
            // 只保留真正的字幕 / 歌词文件（排除说明性 txt，如「読んでね♪.txt」）
            val textFiles = textFolders.flatMap { it.second }.filterText()
            val imageFiles = imageFolders.flatMap { it.second }.filterImage()
                .ifEmpty { rootFiles.filterImage() }
            // 字幕 / 歌词 + 封面汇总成「配套文件池」，按音频同名跨文件夹一一匹配
            val sidecar = textFiles + imageFiles
            audioFiles.sortedBy { it.name }.map { audio -> buildTrack(audio, sidecar) }
        } else {
            rootFiles.filterAudio().sortedBy { it.name }
                .map { audio -> buildTrack(audio, rootFiles) }
        }
    }

    /** 用户所选文件夹（树）自身的最后一级名称，用于判断是否直接选了「音频」文件夹。 */
    fun pickedLeafName(treeUri: Uri): String? =
        runCatching { SafPaths.leafName(DocumentsContract.getTreeDocumentId(treeUri)) }.getOrNull()

    /**
     * 加载用户通过单文件选择器选中的那一首音频。
     *
     * 单文件授权通常只授予该文件本身的访问权；这里会尽力依据文档路径推导所在文件夹、
     * 枚举同名字幕 / 封面。若系统不允许访问其兄弟文件，则只返回音频本身（字幕 / 封面为空），
     * 由界面提示用户改用「选择文件夹」或手动导入。
     */
    fun scanSingleAudio(context: Context, audioUri: Uri): MediaTrack? {
        persistReadPermission(context, audioUri)
        val audioFile = DocumentFile.fromSingleUri(context, audioUri) ?: return null
        val name = audioFile.name
            ?: audioUri.lastPathSegment?.substringAfterLast('/')
            ?: "未知音频"

        // 尽力枚举同目录兄弟文件；任何安全 / 不支持异常都降级为「仅音频」
        val siblings = runCatching { listSiblingFiles(context, audioUri) }
            .getOrDefault(emptyList())
        val fakeAudio = audioFile
        return if (siblings.isNotEmpty()) {
            buildTrack(fakeAudio, siblings)
        } else {
            MediaTrack(audioUri = audioUri, displayName = name)
        }
    }

    /** 对单文件选择器返回的 Uri 持久化只读授权。 */
    fun persistReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /**
     * 由单个音频文档推导其父文件夹并列出其中的文件。
     * 仅在系统允许（同一授权范围）时成功，否则抛异常，由调用方降级。
     */
    private fun listSiblingFiles(context: Context, audioUri: Uri): List<DocumentFile> {
        val docId = runCatching { DocumentsContract.getDocumentId(audioUri) }.getOrNull()
            ?: return emptyList()
        val parentId = SafPaths.parentDocumentId(docId) ?: return emptyList()
        val treeUri = DocumentsContract.buildTreeDocumentUri(audioUri.authority, parentId)
        val parent = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        return parent.listFiles().filter { it.isFile }
    }

    /** 依据同文件夹的全部文件，为一首音频匹配字幕（优先）/ 歌词与封面。 */
    private fun buildTrack(audio: DocumentFile, files: List<DocumentFile>): MediaTrack {
        val name = audio.name ?: "未知音频"
        val base = SafPaths.baseName(name)
        val allNames = files.mapNotNull { it.name }

        val matched = SubtitleMatcher.match(base, allNames)
        val subtitleFile = matched?.let { m -> files.firstOrNull { it.name == m.fileName } }
        val coverFile = files.firstOrNull { f ->
            val n = f.name ?: return@firstOrNull false
            SafPaths.baseName(n).equals(base, ignoreCase = true) && extOf(n) in IMAGE_EXT
        }
        return MediaTrack(
            audioUri = audio.uri,
            displayName = name,
            subtitleUri = subtitleFile?.uri,
            subtitleFormat = matched?.format,
            coverUri = coverFile?.uri,
        )
    }

    private fun extOf(fileName: String?): String =
        fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()

    /** 子文件夹内的直属文件（不继续向下递归）。 */
    private fun immediateFiles(folder: DocumentFile): List<DocumentFile> =
        folder.listFiles().filter { it.isFile }

    private fun List<DocumentFile>.filterAudio(): List<DocumentFile> =
        filter { extOf(it.name) in AUDIO_EXT }

    private fun List<DocumentFile>.filterText(): List<DocumentFile> =
        filter { SubtitleFormat.fromFileName(it.name) != null }

    private fun List<DocumentFile>.filterImage(): List<DocumentFile> =
        filter { extOf(it.name) in IMAGE_EXT }

    /**
     * 深度检索结果：递归遍历整个授权文件夹树后，为当前曲目匹配到的字幕 / 歌词。
     * @param candidateCount 遍历过程中收集到的全部字幕 / 歌词文件数（含未匹配的），用于提示。
     */
    data class DeepSubtitleResult(
        val file: DocumentFile,
        val match: SubtitleMatch,
        val candidateCount: Int,
    )

    /**
     * 深度检索：递归遍历 [rootTreeUri] 下的**全部子目录**，收集所有字幕 / 歌词文件，
     * 再用 [SubtitleMatcher.matchDeep]（严格同名 + 模糊包含）为 [audioBaseName] 匹配。
     *
     * 与 [scan] 的区别：scan 只看「音频所在文件夹 / 同级分类文件夹」的直属文件；
     * 本方法会继续向下钻取（如 `文本/TC/xxx.srt`），并允许跨目录、跨层级匹配。
     * 递归深度上限 [MAX_DEPTH]，避免符号链接 / 异常结构导致死循环。
     *
     * @return 匹配到的最佳结果；没有任何字幕文件或全部不匹配时返回 null。
     */
    fun deepSearchSubtitle(
        context: Context,
        rootTreeUri: Uri,
        audioBaseName: String,
    ): DeepSubtitleResult? {
        val root = DocumentFile.fromTreeUri(context, rootTreeUri) ?: return null
        val allText = ArrayList<DocumentFile>()
        collectTextRecursive(root, allText, depth = 0)
        if (allText.isEmpty()) return null
        val names = allText.mapNotNull { it.name }
        val match = SubtitleMatcher.matchDeep(audioBaseName, names) ?: return null
        val file = allText.firstOrNull { it.name == match.fileName } ?: return null
        return DeepSubtitleResult(file, match, allText.size)
    }

    private const val MAX_DEPTH = 8

    /** 递归收集子树内所有字幕 / 歌词文件（按扩展名判定，排除说明性 txt）。 */
    private fun collectTextRecursive(
        folder: DocumentFile,
        out: MutableList<DocumentFile>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            when {
                child.isDirectory -> collectTextRecursive(child, out, depth + 1)
                child.isFile && SubtitleFormat.fromFileName(child.name) != null -> out.add(child)
            }
        }
    }
}
