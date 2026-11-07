package com.sandianshui.player.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafPathsTest {

    @Test
    fun parentDocumentId_nestedFile_returnsParentFolder() {
        assertEquals(
            "primary:Music/sandianshui",
            SafPaths.parentDocumentId("primary:Music/sandianshui/静夜思.wav"),
        )
    }

    @Test
    fun parentDocumentId_multipleLevels_returnsImmediateParent() {
        assertEquals(
            "primary:A/B/C",
            SafPaths.parentDocumentId("primary:A/B/C/song.mp3"),
        )
    }

    @Test
    fun parentDocumentId_fileAtRoot_returnsNull() {
        // 根目录文件文档 id 形如 primary:song.wav，不含 '/'
        assertNull(SafPaths.parentDocumentId("primary:song.wav"))
    }

    @Test
    fun baseName_stripsLastExtension() {
        assertEquals("静夜思", SafPaths.baseName("静夜思.wav"))
        assertEquals("a.b", SafPaths.baseName("a.b.mp3"))
        assertEquals("noext", SafPaths.baseName("noext"))
    }

    @Test
    fun leafName_nestedDocument_returnsLastSegment() {
        assertEquals("audio", SafPaths.leafName("primary:Asmr/audio"))
        assertEquals("song.mp3", SafPaths.leafName("primary:Asmr/audio/song.mp3"))
    }

    @Test
    fun leafName_topLevelDocument_stripsVolumePrefix() {
        assertEquals("Asmr", SafPaths.leafName("primary:Asmr"))
        assertEquals("song.wav", SafPaths.leafName("primary:song.wav"))
    }
}
