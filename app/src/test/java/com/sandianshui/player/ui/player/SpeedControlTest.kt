package com.sandianshui.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedControlTest {

    @Test
    fun coerceSpeed_clampsBelowMinimum() {
        assertEquals(MIN_SPEED, coerceSpeed(0.1f), 0.0001f)
        assertEquals(MIN_SPEED, coerceSpeed(-2f), 0.0001f)
    }

    @Test
    fun coerceSpeed_clampsAboveMaximum() {
        assertEquals(MAX_SPEED, coerceSpeed(6f), 0.0001f)
        assertEquals(MAX_SPEED, coerceSpeed(100f), 0.0001f)
    }

    @Test
    fun coerceSpeed_keepsValueWithinRange() {
        assertEquals(2.3f, coerceSpeed(2.3f), 0.0001f)
        assertEquals(1.0f, coerceSpeed(1.0f), 0.0001f)
    }

    @Test
    fun speedLabel_formatsOneDecimalWithX() {
        assertEquals("0.5x", speedLabel(0.5f))
        assertEquals("1.0x", speedLabel(1.0f))
        assertEquals("5.0x", speedLabel(5.0f))
    }
}
