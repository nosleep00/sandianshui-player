package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 时间轴点击跳转的纯逻辑回归：点击第 i 句（跳到它的 startMs）后，
 * 该时刻应正好命中第 i 句，且悬浮框切换为该句文本。
 */
class CueClickSeekTest {

    private val cues = listOf(
        Cue(640, 2_660, "阁下，向您报告。"),
        Cue(3_920, 11_580, "第二句（很长，用来验证点击后仍能完整显示）"),
        Cue(12_000, 15_000, "第三句"),
    )

    @Test
    fun everyCueStartReachesThatCue() {
        cues.forEachIndexed { i, cue ->
            assertEquals("点击第 $i 句应命中第 $i 句", i, cues.activeIndexAt(cue.startMs))
        }
    }

    @Test
    fun clickingLaterCueWhileEarlierMovesForward() {
        // 假设当前停在第一句，点击第三句，跳转后应命中第三句
        val target = cues[2].startMs
        assertEquals(2, cues.activeIndexAt(target))
    }

    @Test
    fun floatingBoxSwitchesToClickedCue() {
        // 命中点击的句子时，悬浮框显示该句，而不是保留旧的「上一句」
        val i = 1
        assertEquals(cues[i].text, cues.displayedText(i, "上一句"))
    }
}
