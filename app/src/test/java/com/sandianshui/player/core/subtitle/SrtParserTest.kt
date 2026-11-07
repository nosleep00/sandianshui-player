package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SrtParserTest {

    private val sample =
        "\uFEFF1\r\n" +
            "00:00:01,000 --> 00:00:04,000\r\n" +
            "床前明月光\r\n\r\n" +
            "2\r\n" +
            "00:00:05,500 --> 00:00:09,250\r\n" +
            "疑是地上霜\r\n"

    @Test
    fun parsesAllCues() {
        val cues = SrtParser.parse(sample)
        assertEquals(2, cues.size)
    }

    @Test
    fun parsesTimestampsAndText() {
        val first = SrtParser.parse(sample).first()
        assertEquals(1_000L, first.startMs)
        assertEquals(4_000L, first.endMs)
        assertEquals("床前明月光", first.text)

        val second = SrtParser.parse(sample)[1]
        assertEquals(5_500L, second.startMs)
        assertEquals(9_250L, second.endMs)
    }

    @Test
    fun ignoresBomAndCrlf() {
        // BOM 不应出现在首句文本里
        val first = SrtParser.parse(sample).first()
        assertEquals(false, first.text.startsWith("\uFEFF"))
    }

    @Test
    fun malformedInputReturnsEmptyWithoutCrash() {
        assertEquals(0, SrtParser.parse("这不是字幕\n随便几行").size)
    }
}
