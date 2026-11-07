package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiTranslatorTest {

    private val translator = GeminiTranslator(apiKey = "test")

    @Test
    fun `编号译文按顺序对齐`() {
        val content = """
        1.你好
        2.世界
        3.再见
        """.trimIndent()
        val result = translator.parseNumberedResponse(content, 3)
        assertEquals(listOf("你好", "世界", "再见"), result)
    }

    @Test
    fun `顿号冒号格式也能解析`() {
        val content = """
        1、你好
        2：世界
        """.trimIndent()
        val result = translator.parseNumberedResponse(content, 2)
        assertEquals("你好", result[0])
        assertEquals("世界", result[1])
    }

    @Test
    fun `缺失编号位置为空串`() {
        val content = "1.你好\n3.再见"
        val result = translator.parseNumberedResponse(content, 3)
        assertEquals("你好", result[0])
        assertEquals("", result[1])
        assertEquals("再见", result[2])
    }

    @Test
    fun `超出范围的编号被忽略`() {
        val content = "1.你好\n99.越界"
        val result = translator.parseNumberedResponse(content, 1)
        assertEquals(1, result.size)
        assertEquals("你好", result[0])
    }
}
