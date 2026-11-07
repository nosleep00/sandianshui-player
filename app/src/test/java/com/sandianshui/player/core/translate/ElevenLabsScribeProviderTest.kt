package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class ElevenLabsScribeProviderTest {

    private val provider = ElevenLabsScribeProvider(apiKey = "test")

    @Test
    fun `词级时间戳按句末标点聚合为句子`() {
        val json = """
        {
          "language_code": "ja",
          "text": "こんにちは。元気？",
          "words": [
            {"text": "こんにちは", "start": 0.0, "end": 0.8, "type": "word"},
            {"text": "。", "start": 0.8, "end": 0.9, "type": "word"},
            {"text": "元気", "start": 1.5, "end": 2.2, "type": "word"},
            {"text": "？", "start": 2.2, "end": 2.3, "type": "word"}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)

        assertEquals(2, sentences.size)
        assertEquals("こんにちは。", sentences[0].text)
        assertEquals(0L, sentences[0].beginMs)
        assertEquals(900L, sentences[0].endMs)
        assertEquals("元気？", sentences[1].text)
        assertEquals(1500L, sentences[1].beginMs)
    }

    @Test
    fun `长停顿导致断句`() {
        val json = """
        {
          "words": [
            {"text": "第一句", "start": 0.0, "end": 1.0},
            {"text": "第二句", "start": 3.0, "end": 4.0}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(2, sentences.size)
        assertEquals("第一句", sentences[0].text)
        assertEquals("第二句", sentences[1].text)
    }

    @Test
    fun `拉丁词之间保留空格`() {
        val json = """
        {
          "words": [
            {"text": "Hello", "start": 0.0, "end": 0.5},
            {"text": "world", "start": 0.5, "end": 1.0},
            {"text": ".", "start": 1.0, "end": 1.1}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("Hello world.", sentences[0].text)
    }

    @Test
    fun `空 words 返回空列表`() {
        val json = """{"words": []}"""
        assertEquals(0, provider.parseSentences(json).size)
    }

    @Test
    fun `空白词被过滤`() {
        val json = """
        {
          "words": [
            {"text": "  ", "start": 0.0, "end": 0.1},
            {"text": "有效", "start": 0.2, "end": 0.8}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("有效", sentences[0].text)
    }
}
