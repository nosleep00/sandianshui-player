package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class CueActiveIndexTest {

    private val cues = listOf(
        Cue(1_000, 4_000, "第一句"),
        Cue(5_000, 9_000, "第二句"),
        Cue(11_000, 20_000, "第三句（很长，用来验证完整显示不截断，但测试只关心命中）"),
    )

    @Test
    fun highlightsCueContainingPosition() {
        assertEquals(0, cues.activeIndexAt(1_000)) // 含起点
        assertEquals(0, cues.activeIndexAt(3_999))
        assertEquals(1, cues.activeIndexAt(6_000))
        assertEquals(2, cues.activeIndexAt(15_000))
    }

    @Test
    fun endBoundaryIsExclusive() {
        assertEquals(-1, cues.activeIndexAt(4_000)) // 等于结束时间不命中本句
        assertEquals(-1, cues.activeIndexAt(9_000))
    }

    @Test
    fun gapReturnsMinusOne() {
        assertEquals(-1, cues.activeIndexAt(0))
        assertEquals(-1, cues.activeIndexAt(4_500))
        assertEquals(-1, cues.activeIndexAt(10_000))
    }
}
