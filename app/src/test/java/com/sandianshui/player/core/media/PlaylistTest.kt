package com.sandianshui.player.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTest {

    @Test
    fun emptyPlaylist_hasNoTrackAndNoNavigation() {
        val p = Playlist<String>()
        assertEquals(0, p.size)
        assertNull(p.current)
        assertFalse(p.canPrevious)
        assertFalse(p.canNext)
    }

    @Test
    fun singleTrack_bothDirectionsDisabled() {
        val p = Playlist(listOf("a"))
        assertEquals("a", p.current)
        assertFalse(p.canPrevious)
        assertFalse(p.canNext)
        // 到端继续切换不应移动
        assertEquals("a", p.previous())
        assertEquals("a", p.next())
        assertEquals(0, p.currentIndex)
    }

    @Test
    fun multipleTracks_nextAndPreviousMoveWithinBounds() {
        val p = Playlist(listOf("a", "b", "c"))
        assertFalse(p.canPrevious)
        assertTrue(p.canNext)

        assertEquals("b", p.next())
        assertEquals(1, p.currentIndex)
        assertTrue(p.canPrevious)
        assertTrue(p.canNext)

        assertEquals("c", p.next())
        assertEquals(2, p.currentIndex)
        assertTrue(p.canPrevious)
        assertFalse(p.canNext)
        // 已到最后，next 不再移动
        assertEquals("c", p.next())
        assertEquals(2, p.currentIndex)

        assertEquals("b", p.previous())
        assertEquals("a", p.previous())
        assertEquals(0, p.currentIndex)
        assertFalse(p.canPrevious)
    }

    @Test
    fun setOutOfRangeIndex_isClamped() {
        val p = Playlist<String>()
        p.set(listOf("a", "b"), index = 99)
        assertEquals(1, p.currentIndex)
        assertEquals("b", p.current)
    }

    @Test
    fun updateCurrent_persistsAcrossSwitching() {
        val p = Playlist(listOf("a", "b"))
        p.updateCurrent("a+字幕")
        p.next()
        p.previous()
        assertEquals("a+字幕", p.current)
    }
}
