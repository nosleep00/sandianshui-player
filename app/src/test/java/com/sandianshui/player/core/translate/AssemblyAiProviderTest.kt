package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class AssemblyAiProviderTest {

    private val provider = AssemblyAiProvider(apiKey = "test")

    @Test
    fun `词级时间戳按句末标点聚合为句子`() {
        val json = """
        {
          "status": "completed",
          "text": "こんにちは。元気？",
          "words": [
            {"text": "こんにちは", "start": 0, "end": 800},
            {"text": "。", "start": 800, "end": 900},
            {"text": "元気", "start": 1500, "end": 2200},
            {"text": "？", "start": 2200, "end": 2300}
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
    fun `毫秒时间戳不再乘以1000`() {
        val json = """
        {
          "words": [
            {"text": "Hello", "start": 500, "end": 1000},
            {"text": "world", "start": 1000, "end": 1500},
            {"text": ".", "start": 1500, "end": 1600}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("Hello world.", sentences[0].text)
        assertEquals(500L, sentences[0].beginMs)
        assertEquals(1600L, sentences[0].endMs)
    }

    @Test
    fun `长停顿导致断句`() {
        val json = """
        {
          "words": [
            {"text": "第一句", "start": 0, "end": 1000},
            {"text": "第二句", "start": 3000, "end": 4000}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(2, sentences.size)
        assertEquals("第一句", sentences[0].text)
        assertEquals("第二句", sentences[1].text)
    }

    @Test
    fun `空 words 返回空列表`() {
        assertEquals(0, provider.parseSentences("""{"words": []}""").size)
    }

    @Test
    fun `空白词被过滤`() {
        val json = """
        {
          "words": [
            {"text": "  ", "start": 0, "end": 100},
            {"text": "有效", "start": 200, "end": 800}
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("有效", sentences[0].text)
    }
}
