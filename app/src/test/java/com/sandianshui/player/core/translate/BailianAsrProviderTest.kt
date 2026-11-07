package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BailianAsrProviderTest {

    private val provider = BailianAsrProvider(apiKey = "test")

    @Test
    fun `标准sentences解析正确`() {
        val json = """
        {
          "transcripts": [
            {
              "sentences": [
                {"begin_time": 100, "end_time": 2000, "text": "こんにちは"},
                {"begin_time": 2000, "end_time": 5000, "text": "世界"}
              ]
            }
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(2, sentences.size)
        assertEquals(100, sentences[0].beginMs)
        assertEquals(2000, sentences[0].endMs)
        assertEquals("こんにちは", sentences[0].text)
        assertEquals(2000, sentences[1].beginMs)
        assertEquals(5000, sentences[1].endMs)
        assertEquals("世界", sentences[1].text)
    }

    @Test
    fun `空transcripts返回空列表`() {
        val json = """{"transcripts": []}"""
        assertTrue(provider.parseSentences(json).isEmpty())
    }

    @Test
    fun `缺少transcripts返回空列表`() {
        val json = """{"foo": "bar"}"""
        assertTrue(provider.parseSentences(json).isEmpty())
    }

    @Test
    fun `过滤空文本句子`() {
        val json = """
        {
          "transcripts": [
            {
              "sentences": [
                {"begin_time": 0, "end_time": 1000, "text": "  "},
                {"begin_time": 1000, "end_time": 2000, "text": "有效"}
              ]
            }
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("有效", sentences[0].text)
    }

    @Test
    fun `过滤过短句子（小于200ms）`() {
        val json = """
        {
          "transcripts": [
            {
              "sentences": [
                {"begin_time": 0, "end_time": 100, "text": "太短"},
                {"begin_time": 100, "end_time": 1000, "text": "正常"}
              ]
            }
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("正常", sentences[0].text)
    }

    @Test
    fun `过滤时间无效的句子`() {
        val json = """
        {
          "transcripts": [
            {
              "sentences": [
                {"begin_time": -1, "end_time": 1000, "text": "负开始"},
                {"begin_time": 2000, "end_time": 1000, "text": "结束早于开始"},
                {"begin_time": 1000, "end_time": 2000, "text": "有效"}
              ]
            }
          ]
        }
        """.trimIndent()
        val sentences = provider.parseSentences(json)
        assertEquals(1, sentences.size)
        assertEquals("有效", sentences[0].text)
    }
}
