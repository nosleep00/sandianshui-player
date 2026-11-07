package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleFormatTest {

    @Test
    fun recognizesNormalExtensions() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("a.srt"))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.fromFileName("a.vtt"))
        assertEquals(SubtitleFormat.LRC, SubtitleFormat.fromFileName("a.lrc"))
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("a.SRT"))
    }

    @Test
    fun recognizesDoubleExtensionSavedAsTxt() {
        // 浏览器 / 记事本另存为 .txt：以倒数第二个扩展名为准
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("a.srt.txt"))
        assertEquals(SubtitleFormat.VTT, SubtitleFormat.fromFileName("a.vtt.TXT"))
        assertEquals(SubtitleFormat.LRC, SubtitleFormat.fromFileName("a.lrc.txt"))
    }

    @Test
    fun plainTextAndUnknownReturnNull() {
        assertNull(SubtitleFormat.fromFileName("読んでね.txt"))
        assertNull(SubtitleFormat.fromFileName("notes.txt"))
        assertNull(SubtitleFormat.fromFileName("cover.png"))
        assertNull(SubtitleFormat.fromFileName(null))
        assertNull(SubtitleFormat.fromFileName(""))
    }

    @Test
    fun variantBeforeTxtStillRecognized() {
        assertEquals(SubtitleFormat.SRT, SubtitleFormat.fromFileName("a.zh.srt.txt"))
        assertEquals(SubtitleFormat.LRC, SubtitleFormat.fromFileName("a.zh.lrc.txt"))
    }
}
