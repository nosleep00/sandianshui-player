package com.sandianshui.player.core.translate

import com.sandianshui.player.core.translate.model.AsrSentence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleComposerTest {

    @Test
    fun `空句子生成空字符串`() {
        assertEquals("", SubtitleComposer.toSrt(emptyList()))
    }

    @Test
    fun `单句生成正确SRT格式`() {
        val sentences = listOf(AsrSentence(100, 3820, "你好"))
        val srt = SubtitleComposer.toSrt(sentences)
        val lines = srt.lines()
        assertEquals("1", lines[0])
        assertEquals("00:00:00,100 --> 00:00:03,820", lines[1])
        assertEquals("你好", lines[2])
    }

    @Test
    fun `多句序号递增时间正确`() {
        val sentences = listOf(
            AsrSentence(0, 5000, "第一句"),
            AsrSentence(5000, 10000, "第二句"),
            AsrSentence(65000, 70000, "第三句"),
        )
        val srt = SubtitleComposer.toSrt(sentences)
        assertTrue(srt.contains("1\n00:00:00,000 --> 00:00:05,000\n第一句"))
        assertTrue(srt.contains("2\n00:00:05,000 --> 00:00:10,000\n第二句"))
        assertTrue(srt.contains("3\n00:01:05,000 --> 00:01:10,000\n第三句"))
    }

    @Test
    fun `使用译文替换原文`() {
        val sentences = listOf(AsrSentence(0, 1000, "こんにちは"))
        val srt = SubtitleComposer.toSrt(sentences, listOf("你好"))
        assertTrue(srt.contains("你好"))
        assertTrue(!srt.contains("こんにちは"))
    }

    @Test
    fun `译文为空时回退到原文`() {
        val sentences = listOf(AsrSentence(0, 1000, "hello"))
        val srt = SubtitleComposer.toSrt(sentences, listOf(""))
        assertTrue(srt.contains("hello"))
    }

    @Test
    fun `超过一小时的时间戳正确`() {
        val sentences = listOf(AsrSentence(3_661_000, 3_662_000, "长音频"))
        val srt = SubtitleComposer.toSrt(sentences)
        assertTrue(srt.contains("01:01:01,000 --> 01:01:02,000"))
    }
}
