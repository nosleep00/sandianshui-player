package com.sandianshui.player.core.subtitle

/** 字幕 / 歌词解析器统一接口。 */
interface SubtitleParser {
    val format: SubtitleFormat

    /** 把文件原始文本解析成按开始时间升序排列的 Cue 列表；解析失败返回空列表，不抛异常。 */
    fun parse(rawText: String): List<Cue>
}

/** 按格式取得对应解析器。 */
object SubtitleParsers {
    fun forFormat(format: SubtitleFormat): SubtitleParser = when (format) {
        SubtitleFormat.SRT -> SrtParser
        SubtitleFormat.VTT -> VttParser
        SubtitleFormat.LRC -> LrcParser
    }

    /** 便捷方法：按格式解析原始文本。 */
    fun parse(format: SubtitleFormat, rawText: String): List<Cue> =
        forFormat(format).parse(rawText)
}
