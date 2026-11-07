package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VttParserTest {

    private val sample =
        "WEBVTT\n\n" +
            "00:00:01.000 --> 00:00:04.000 align:start position:0%\n" +
            "<i>床前</i>明月光\n\n" +
            "NOTE 这是一条注释，应被忽略\n\n" +
            "00:05.000 --> 00:09.000\n" +
            "疑是地上霜\n"

    @Test
    fun parsesCuesAndSkipsHeaderAndNote() {
        val cues = VttParser.parse(sample)
        assertEquals(2, cues.size)
    }

    @Test
    fun stripsInlineTagsAndCueSettings() {
        val first = VttParser.parse(sample).first()
        assertEquals(1_000L, first.startMs)
        assertEquals(4_000L, first.endMs)
        assertEquals("床前明月光", first.text)
        assertFalse(first.text.contains("<"))
    }

    @Test
    fun supportsMinuteSecondTimestamp() {
        val second = VttParser.parse(sample)[1]
        assertEquals(5_000L, second.startMs)
        assertEquals(9_000L, second.endMs)
    }
}
