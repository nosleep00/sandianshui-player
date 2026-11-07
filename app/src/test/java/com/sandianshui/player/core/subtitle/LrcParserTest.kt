package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {

    private val sample =
        "[ti:静夜思]\n" +
            "[ar:李白]\n" +
            "[00:01.00]床前明月光\n" +
            "[00:05.50][00:30.00]疑是地上霜\n"

    @Test
    fun skipsMetadataAndParsesTimedLines() {
        val cues = LrcParser.parse(sample)
        // 第二行带两个时间标签，应展开为两句
        assertEquals(3, cues.size)
    }

    @Test
    fun parsesCentisecondTimestamps() {
        val first = LrcParser.parse(sample).first()
        assertEquals(1_000L, first.startMs)
        assertEquals("床前明月光", first.text)
    }

    @Test
    fun expandsMultipleTagsOnOneLine() {
        val cues = LrcParser.parse(sample)
        val repeated = cues.filter { it.text == "疑是地上霜" }
        assertEquals(2, repeated.size)
        assertEquals(5_500L, repeated[0].startMs)
        assertEquals(30_000L, repeated[1].startMs)
    }

    @Test
    fun endTimeDefaultsToNextLineStart() {
        val cues = LrcParser.parse(sample)
        // 第一句结束时间 = 下一句开始时间 5.5s
        assertEquals(5_500L, cues[0].endMs)
    }
}
