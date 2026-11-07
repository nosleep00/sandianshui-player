package com.sandianshui.player.core.subtitle

/**
 * SRT 解析器。典型块：
 *
 * 1
 * 00:00:01,000 --> 00:00:04,000
 * 床前明月光
 */
object SrtParser : SubtitleParser {
    override val format = SubtitleFormat.SRT

    private val ARROW = "-->"

    override fun parse(rawText: String): List<Cue> {
        val text = SubtitleText.clean(rawText)
        val cues = mutableListOf<Cue>()
        for (block in SubtitleText.splitBlocks(text)) {
            val arrowIndex = block.indexOfFirst { it.contains(ARROW) }
            if (arrowIndex < 0) continue
            val timing = block[arrowIndex].split(ARROW)
            val start = SubtitleText.parseTimeMs(timing.getOrNull(0).orEmpty()) ?: continue
            val end = SubtitleText.parseTimeMs(timing.getOrNull(1).orEmpty().trim().substringBefore(' '))
                ?: continue
            val body = block.subList(arrowIndex + 1, block.size)
                .joinToString("\n") { it.trim() }
                .trim()
            if (body.isNotEmpty()) cues.add(Cue(start, end, body))
        }
        return cues.sortedBy { it.startMs }
    }
}
