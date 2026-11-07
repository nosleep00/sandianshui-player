package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class SubtitleDecoderTest {

    private val jaSrt =
        "1\n00:00:00,360 --> 00:00:02,120\nお疲れ様です。\n書類、大量じゃないですか。\n"
    private val zhSrt = "1\n00:00:01,000 --> 00:00:02,000\n让您久等了。\n文件也太多了吧。\n"

    @Test
    fun utf8WithoutBom_detected() {
        val bytes = jaSrt.toByteArray(Charsets.UTF_8)
        assertEquals(SubtitleEncoding.UTF8, SubtitleDecoder.detect(bytes))
        assertEquals(jaSrt, SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO))
    }

    @Test
    fun utf8WithBom_detectedAndStripped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            zhSrt.toByteArray(Charsets.UTF_8)
        assertEquals(SubtitleEncoding.UTF8, SubtitleDecoder.detect(bytes))
        // clean 会去掉 BOM，句首不应有占位字符
        assertTrue(SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO).startsWith("1"))
    }

    @Test
    fun utf16LeWithBom_detected() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            jaSrt.toByteArray(Charset.forName("UTF-16LE"))
        assertEquals(SubtitleEncoding.UTF16LE, SubtitleDecoder.detect(bytes))
        assertEquals(jaSrt, SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO))
    }

    @Test
    fun utf16LeWithoutBom_detectedByAsciiHeuristic() {
        val content = "00:00:01,000 --> 00:00:02,000\nhello world\n"
        val bytes = content.toByteArray(Charset.forName("UTF-16LE"))
        assertEquals(SubtitleEncoding.UTF16LE, SubtitleDecoder.detect(bytes))
        assertEquals(content, SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO))
    }

    @Test
    fun shiftJisJapanese_detectedAndDecoded() {
        val bytes = jaSrt.toByteArray(Charset.forName("Shift_JIS"))
        assertFalse(jaSrt.toByteArray(Charsets.UTF_8).contentEquals(bytes))
        assertEquals(SubtitleEncoding.SHIFT_JIS, SubtitleDecoder.detect(bytes))
        assertEquals(jaSrt, SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO))
    }

    @Test
    fun gbkChinese_detectedAndDecoded() {
        val bytes = zhSrt.toByteArray(Charset.forName("GBK"))
        assertEquals(SubtitleEncoding.GBK, SubtitleDecoder.detect(bytes))
        assertEquals(zhSrt, SubtitleDecoder.decode(bytes, SubtitleEncoding.AUTO))
    }

    @Test
    fun manualSelectionOverridesDetection() {
        // 同一份 GBK 字节，显式指定 GBK 必须解出中文
        val bytes = zhSrt.toByteArray(Charset.forName("GBK"))
        assertEquals(zhSrt, SubtitleDecoder.decode(bytes, SubtitleEncoding.GBK))
        // 显式指定 UTF-8 也不崩溃（得到乱码或替代字符，由用户再切换）
        SubtitleDecoder.decode(bytes, SubtitleEncoding.UTF8)
    }

    @Test
    fun emptyBytes_returnsEmpty() {
        assertEquals("", SubtitleDecoder.decode(ByteArray(0), SubtitleEncoding.AUTO))
        assertEquals(SubtitleEncoding.UTF8, SubtitleDecoder.detect(ByteArray(0)))
    }
}
