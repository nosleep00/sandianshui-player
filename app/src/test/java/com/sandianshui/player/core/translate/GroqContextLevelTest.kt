package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GroqContextLevel] 档位解析与耗时预估的单元测试。
 */
class GroqContextLevelTest {

    @Test
    fun `共五档且标签正确`() {
        assertEquals(
            listOf("弱", "较弱", "中", "较强", "强"),
            GroqContextLevel.entries.map { it.label },
        )
    }

    @Test
    fun `按档位序号解析`() {
        assertEquals(GroqContextLevel.WEAK, GroqContextLevel.fromLevel(1))
        assertEquals(GroqContextLevel.STRONG, GroqContextLevel.fromLevel(5))
    }

    @Test
    fun `越界序号回退默认`() {
        assertEquals(GroqContextLevel.DEFAULT, GroqContextLevel.fromLevel(0))
        assertEquals(GroqContextLevel.DEFAULT, GroqContextLevel.fromLevel(99))
    }

    @Test
    fun `按下标解析`() {
        assertEquals(GroqContextLevel.MEDIUM, GroqContextLevel.fromOrdinal(2))
    }

    @Test
    fun `耗时预估按批次计算`() {
        // 10 句 = 1 批，中档每批 20 秒
        assertEquals(20, GroqContextLevel.MEDIUM.estimateSeconds(10))
        // 11 句 = 2 批
        assertEquals(40, GroqContextLevel.MEDIUM.estimateSeconds(11))
        // 85 句 = 9 批
        assertEquals(180, GroqContextLevel.MEDIUM.estimateSeconds(85))
    }

    @Test
    fun `档位越高耗时倍数越大`() {
        val multipliers = GroqContextLevel.entries.map { it.timeMultiplier }
        assertEquals(1.0, multipliers.first(), 0.001)
        assertTrue(multipliers.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `空句子预估为零`() {
        assertEquals(0, GroqContextLevel.STRONG.estimateSeconds(0))
    }

    @Test
    fun `强档使用深度推理和最多上下文`() {
        assertEquals("high", GroqContextLevel.STRONG.reasoningEffort)
        assertEquals(4, GroqContextLevel.STRONG.contextSentences)
    }
}
