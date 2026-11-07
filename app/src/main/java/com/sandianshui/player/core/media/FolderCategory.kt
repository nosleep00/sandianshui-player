package com.sandianshui.player.core.media

/** 按文件夹名识别出的资源类别（用于音频 / 字幕分文件夹布局）。 */
enum class FolderCategory { AUDIO, SUBTITLE, LYRICS, IMAGE, UNKNOWN }

/**
 * 依据文件夹名判断它存放的资源类别。纯字符串逻辑、不依赖 Android，便于单测。
 * 同时支持英文关键字与中文目录名：英文按「词元」精确匹配（避免 sub 误命中 subject），
 * 中文按关键字包含匹配（中文词之间不空格）。
 */
object FolderCategories {

    private val AUDIO_ASCII = setOf(
        "audio", "music", "sound", "sounds", "track", "voice", "voices", "song", "songs",
    )
    private val AUDIO_CJK = listOf("音频", "音乐", "声音", "歌曲", "配音")

    private val SUBTITLE_ASCII = setOf(
        "subtitle", "subtitles", "sub", "caption", "captions", "srt", "vtt",
    )
    private val SUBTITLE_CJK = listOf("字幕", "文本", "台本", "台词")

    private val LYRIC_ASCII = setOf("lyric", "lyrics", "lrc")
    private val LYRIC_CJK = listOf("歌词")

    private val IMAGE_ASCII = setOf(
        "image", "images", "cover", "covers", "picture", "pictures", "pic", "pics",
        "img", "photo", "photos",
    )
    private val IMAGE_CJK = listOf("封面", "图片", "图像", "写真")

    fun categoryOf(folderName: String?): FolderCategory {
        if (folderName.isNullOrBlank()) return FolderCategory.UNKNOWN
        val lower = folderName.lowercase()
        val asciiTokens = Regex("[a-z0-9]+").findAll(lower).map { it.value }.toList()
        val cjkSegments = Regex("[\u4e00-\u9fff]+").findAll(lower).map { it.value }.toList()

        // 先判更具体的歌词、字幕，再图片，最后音频
        if (matches(LYRIC_ASCII, LYRIC_CJK, asciiTokens, cjkSegments)) return FolderCategory.LYRICS
        if (matches(SUBTITLE_ASCII, SUBTITLE_CJK, asciiTokens, cjkSegments)) return FolderCategory.SUBTITLE
        if (matches(IMAGE_ASCII, IMAGE_CJK, asciiTokens, cjkSegments)) return FolderCategory.IMAGE
        if (matches(AUDIO_ASCII, AUDIO_CJK, asciiTokens, cjkSegments)) return FolderCategory.AUDIO
        return FolderCategory.UNKNOWN
    }

    private fun matches(
        asciiWords: Set<String>,
        cjkWords: List<String>,
        asciiTokens: List<String>,
        cjkSegments: List<String>,
    ): Boolean =
        asciiTokens.any { it in asciiWords } ||
            cjkSegments.any { seg -> cjkWords.any { seg.contains(it) } }
}
