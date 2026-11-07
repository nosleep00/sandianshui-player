package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayedCueTextTest {

    private val cues = listOf(
        Cue(0, 1000, "第一句"),
        Cue(1000, 2000, "第二句"),
        Cue(2000, 3000, "第三句特别长特别长特别长特别长"),
    )

    @Test
    fun activeIndex_valid_showsThatLine() {
        assertEquals("第一句", cues.displayedText(activeIndex = 0, previous = null))
        assertEquals("第二句", cues.displayedText(activeIndex = 1, previous = "第一句"))
    }

    @Test
    fun activeIndex_minusOne_afterLineEnded_keepsPreviousLine() {
        // 第二句结束、第三句尚未开始（句间空隙），不应清空，保留第二句
        assertEquals("第二句", cues.displayedText(activeIndex = -1, previous = "第二句"))
    }

    @Test
    fun activeIndex_minusOne_afterLastLine_keepsLastLine() {
        // 全部播完后，悬浮框仍显示最后一句，直到被下一句替代
        val last = cues.last().text
        assertEquals(last, cues.displayedText(activeIndex = -1, previous = last))
    }

    @Test
    fun activeIndex_minusOne_beforeFirstLine_returnsNull() {
        assertNull(cues.displayedText(activeIndex = -1, previous = null))
    }

    @Test
    fun activeIndex_outOfRange_keepsPrevious() {
        assertEquals("第二句", cues.displayedText(activeIndex = 99, previous = "第二句"))
    }
}
