package com.sandianshui.player.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:05", formatTime(5_000))
        assertEquals("02:30", formatTime(150_000))
    }

    @Test
    fun negativeClampsToZero() {
        assertEquals("00:00", formatTime(-1_000))
    }
}
