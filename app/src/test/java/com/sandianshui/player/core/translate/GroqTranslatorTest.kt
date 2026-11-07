package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GroqTranslator] 编号译文解析与漏译检测的单元测试。
 */
class GroqTranslatorTest {

    private val translator = GroqTranslator("fake-key")

    @Test
    fun `解析标准编号译文 按顺序对齐`() {
        val content = "1. 你好\n2. 谢谢\n3. 再见"
        val result = translator.parseNumberedResponse(content, 3)
        assertEquals(listOf("你好", "谢谢", "再见"), result)
    }

    @Test
    fun `解析顿号与冒号格式`() {
        val content = "1、你好\n2：谢谢"
        val result = translator.parseNumberedResponse(content, 2)
        assertEquals(listOf("你好", "谢谢"), result)
    }

    @Test
    fun `缺失编号的位置用空串占位`() {
        val content = "1. 你好\n3. 再见"
        val result = translator.parseNumberedResponse(content, 3)
        assertEquals("你好", result[0])
        assertEquals("", result[1])
        assertEquals("再见", result[2])
    }

    @Test
    fun `长句译文完整保留不截断`() {
        val long = "这是一句非常非常长的字幕译文，包含很多内容，用来验证不会被截断或省略"
        val content = "1. $long"
        val result = translator.parseNumberedResponse(content, 1)
        assertEquals(long, result[0])
    }

    @Test
    fun `同一行出现多条编号也能识别`() {
        val content = "1. 你好 2. 谢谢 3. 再见"
        val result = translator.parseNumberedResponse(content, 3)
        assertEquals(listOf("你好", "谢谢", "再见"), result)
    }

    @Test
    fun `长句换行的续行归入上一条`() {
        val content = "1. 这是一句很长的话，\n它在第二行继续，\n第三行结束\n2. 下一句"
        val result = translator.parseNumberedResponse(content, 2)
        assertEquals("这是一句很长的话，\n它在第二行继续，\n第三行结束", result[0])
        assertEquals("下一句", result[1])
    }

    @Test
    fun `右括号与方括号分隔符也识别`() {
        val content = "1）你好\n2】谢谢\n3] 再见"
        val result = translator.parseNumberedResponse(content, 3)
        assertEquals(listOf("你好", "谢谢", "再见"), result)
    }

    @Test
    fun `译文里不符合期望序号的数字不会误判`() {
        // 第一句译文中出现 "3、" 但期望序号是 2，不应被当作第三条标记
        val content = "1. 一共有3、四个东西\n2. 第二句"
        val result = translator.parseNumberedResponse(content, 2)
        assertEquals("一共有3、四个东西", result[0])
        assertEquals("第二句", result[1])
    }

    @Test
    fun `空句判定为未翻译`() {
        assertTrue(translator.isUntranslated("", "zh"))
        assertTrue(translator.isUntranslated(null, "zh"))
        assertTrue(translator.isUntranslated("   ", "zh"))
    }

    @Test
    fun `仍含多个日文假名判定为未翻译`() {
        assertTrue(translator.isUntranslated("こんにちは、今日はいい天気ですね", "zh"))
    }

    @Test
    fun `正常中文译文判定为已翻译`() {
        assertFalse(translator.isUntranslated("你好，今天天气真好啊", "zh"))
    }

    @Test
    fun `目标为日语时不用假名判定漏译`() {
        assertFalse(translator.isUntranslated("こんにちは", "ja"))
    }

    @Test
    fun `单个假名的名字不判定为漏译`() {
        assertFalse(translator.isUntranslated("这是名为ン的角色", "zh"))
    }

    @Test
    fun `弱档不附参考上下文`() {
        val weak = GroqTranslator("fake-key", contextLevel = GroqContextLevel.WEAK)
        val content = weak.buildUserContent(
            listOf("句一", "句二", "句三"),
            listOf(1, 2),
        )
        assertFalse(content.contains("参考上下文"))
        assertTrue(content.contains("1.句二"))
        assertTrue(content.contains("2.句三"))
    }

    @Test
    fun `强档附带前后文且目标句重新编号`() {
        val strong = GroqTranslator("fake-key", contextLevel = GroqContextLevel.STRONG)
        val all = listOf("句0", "句1", "句2", "句3", "句4", "句5")
        val content = strong.buildUserContent(all, listOf(2, 3))
        assertTrue(content.contains("参考上下文"))
        assertTrue(content.contains("（前文）句0"))
        assertTrue(content.contains("（后文）句4"))
        assertTrue(content.contains("1.句2"))
        assertTrue(content.contains("2.句3"))
        assertFalse(content.contains("1.句0"))
    }

    @Test
    fun `上下文不会越界`() {
        val high = GroqTranslator("fake-key", contextLevel = GroqContextLevel.HIGHER)
        val content = high.buildUserContent(listOf("a", "b", "c"), listOf(0, 1))
        assertFalse(content.contains("（前文）"))
        assertTrue(content.contains("（后文）c"))
    }
}
