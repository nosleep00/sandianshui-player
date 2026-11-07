package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekTranslatorTest {

    private val translator = DeepSeekTranslator(apiKey = "test")

    @Test
    fun `标准编号响应正确解析`() {
        val response = "1.你好\n2.世界\n3.测试"
        val result = translator.parseNumberedResponse(response, 3)
        assertEquals("你好", result[0])
        assertEquals("世界", result[1])
        assertEquals("测试", result[2])
    }

    @Test
    fun `中文顿号分隔符也能解析`() {
        val response = "1、你好\n2、世界"
        val result = translator.parseNumberedResponse(response, 2)
        assertEquals("你好", result[0])
        assertEquals("世界", result[1])
    }

    @Test
    fun `冒号分隔符也能解析`() {
        val response = "1: 你好\n2: 世界"
        val result = translator.parseNumberedResponse(response, 2)
        assertEquals("你好", result[0])
        assertEquals("世界", result[1])
    }

    @Test
    fun `缺失编号的位置返回空字符串`() {
        val response = "1.你好\n3.第三句"
        val result = translator.parseNumberedResponse(response, 3)
        assertEquals("你好", result[0])
        assertEquals("", result[1])
        assertEquals("第三句", result[2])
    }

    @Test
    fun `空响应返回全空`() {
        val result = translator.parseNumberedResponse("", 3)
        assertEquals(3, result.size)
        assertTrue(result.all { it.isEmpty() })
    }

    @Test
    fun `译文含编号格式文本不干扰`() {
        val response = "1.他说：1加1等于2\n2.第二句"
        val result = translator.parseNumberedResponse(response, 2)
        assertEquals("他说：1加1等于2", result[0])
        assertEquals("第二句", result[1])
    }

    private fun assertTrue(condition: Boolean) {
        if (!condition) throw AssertionError("Expected true")
    }
}
