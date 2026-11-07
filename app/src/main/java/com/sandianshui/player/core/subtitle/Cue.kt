package com.sandianshui.player.core.subtitle

/**
 * 一句字幕 / 歌词：在 [startMs, endMs) 时间区间内显示 [text]。
 * 三种格式（SRT / VTT / LRC）最终都解析成统一的 Cue 列表。
 */
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** 字幕 / 歌词文件格式。 */
enum class SubtitleFormat(val ext: String, val isSubtitle: Boolean) {
    SRT("srt", true),
    VTT("vtt", true),
    LRC("lrc", false);

    companion object {
        /**
         * 根据文件名后缀判断格式，无法识别返回 null。
         * 兼容「另存为 .txt」：浏览器 / 记事本有时会把字幕存成 `xxx.srt.txt`、
         * `xxx.lrc.txt`，此时以倒数第二个扩展名为准。
         */
        fun fromFileName(fileName: String?): SubtitleFormat? {
            val lower = fileName?.lowercase() ?: return null
            fromExt(lower.substringAfterLast('.', ""))?.let { return it }
            if (lower.endsWith(".txt")) {
                val beforeTxt = lower.removeSuffix(".txt")
                fromExt(beforeTxt.substringAfterLast('.', ""))?.let { return it }
            }
            return null
        }

        private fun fromExt(ext: String): SubtitleFormat? =
            entries.firstOrNull { it.ext == ext }
    }
}

/** 返回 [positionMs] 时刻应高亮的句子下标；没有任何句子命中时返回 -1。 */
fun List<Cue>.activeIndexAt(positionMs: Long): Int =
    indexOfFirst { positionMs >= it.startMs && positionMs < it.endMs }

/**
 * 悬浮字幕框当前应显示的文本：
 * - 命中某一句（[activeIndex] 有效）时显示该句；
 * - 未命中（句间空隙 / 尚未开始 / 已播完）时保留 [previous]，不返回 null，
 *   从而满足「一句说完后不消失，直到被下一句替代」。
 * 第一句开始前（[previous] 为 null）且未命中，返回 null。
 */
fun List<Cue>.displayedText(activeIndex: Int, previous: String?): String? =
    if (activeIndex in indices) this[activeIndex].text else previous
