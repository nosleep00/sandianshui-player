package com.sandianshui.player.core.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayLyricBusTest {

    @Test
    fun updatePublishesTextAndHasCues() {
        OverlayLyricBus.reset()
        OverlayLyricBus.update("当前句", hasCues = true)
        assertEquals("当前句", OverlayLyricBus.content.value.text)
        assertEquals(true, OverlayLyricBus.content.value.hasCues)
    }

    @Test
    fun updateWithNullKeepsPlaceholderState() {
        OverlayLyricBus.reset()
        OverlayLyricBus.update(null, hasCues = true)
        assertNull(OverlayLyricBus.content.value.text)
        // hasCues 为真时界面会显示占位「—」，而不是关闭悬浮窗
        assertEquals(true, OverlayLyricBus.content.value.hasCues)
    }

    @Test
    fun resetClearsContent() {
        OverlayLyricBus.update("句子", hasCues = true)
        OverlayLyricBus.reset()
        assertNull(OverlayLyricBus.content.value.text)
        assertFalse(OverlayLyricBus.content.value.hasCues)
    }
}
