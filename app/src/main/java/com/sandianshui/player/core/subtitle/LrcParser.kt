package com.sandianshui.player.core.subtitle

/**
 * LRC 歌词解析器。典型行：
 *
 * [ti:静夜思]            <- 元数据，忽略
 * [00:01.00]床前明月光
 * [00:05.00][00:30.00]疑是地上霜   <- 一行可带多个时间标签
 *
 * 每行的结束时间取下一句的开始时间；最后一句默认持续 5 秒。
 */
object LrcParser : SubtitleParser {
    override val format = SubtitleFormat.LRC

    private val TIME_TAG = Regex("""\[(\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?)]""")
    private val ANY_TAG = Regex("""\[[^\]]*]""")
    private const val DEFAULT_END_MS = 5_000L

    override fun parse(rawText: String): List<Cue> {
        val text = SubtitleText.clean(rawText)
        val points = mutableListOf<Pair<Long, String>>()

        for (line in text.split('\n')) {
            val tags = TIME_TAG.findAll(line).map { it.groupValues[1] }.toList()
            if (tags.isEmpty()) continue // 没有时间标签的行（元数据等）忽略
            val lyric = line.replace(ANY_TAG, "").trim()
            if (lyric.isEmpty()) continue
            for (tag in tags) {
                val start = SubtitleText.parseTimeMs(tag) ?: continue
                points.add(start to lyric)
            }
        }

        val sorted = points.sortedBy { it.first }
        return sorted.mapIndexed { index, (start, lyric) ->
            val end = sorted.getOrNull(index + 1)?.first ?: (start + DEFAULT_END_MS)
            Cue(start, end, lyric)
        }
    }
}
