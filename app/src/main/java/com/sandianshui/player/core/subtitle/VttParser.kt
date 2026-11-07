package com.sandianshui.player.core.subtitle

/**
 * WebVTT 解析器。典型块：
 *
 * WEBVTT
 *
 * 00:00:01.000 --> 00:00:04.000 align:start position:0%
 * <i>床前明月光</i>
 *
 * 注意：时间戳用点号毫秒，结束时间后可能带 cue 设置，文本可能含行内标签。
 */
object VttParser : SubtitleParser {
    override val format = SubtitleFormat.VTT

    private val ARROW = "-->"

    override fun parse(rawText: String): List<Cue> {
        val text = SubtitleText.clean(rawText)
        val cues = mutableListOf<Cue>()
        for (block in SubtitleText.splitBlocks(text)) {
            // 跳过 WEBVTT 头、NOTE 注释、REGION/STYLE 等非字幕块
            val first = block.firstOrNull().orEmpty()
            if (first.startsWith("WEBVTT") ||
                first.startsWith("NOTE") ||
                first.startsWith("REGION") ||
                first.startsWith("STYLE")
            ) continue

            val arrowIndex = block.indexOfFirst { it.contains(ARROW) }
            if (arrowIndex < 0) continue

            val timing = block[arrowIndex].split(ARROW)
            val start = SubtitleText.parseTimeMs(timing.getOrNull(0).orEmpty()) ?: continue
            val endToken = timing.getOrNull(1).orEmpty().trim().substringBefore(' ')
            val end = SubtitleText.parseTimeMs(endToken) ?: continue

            val body = block.subList(arrowIndex + 1, block.size)
                .joinToString("\n") { SubtitleText.stripTags(it) }
                .trim()
            if (body.isNotEmpty()) cues.add(Cue(start, end, body))
        }
        return cues.sortedBy { it.startMs }
    }
}
