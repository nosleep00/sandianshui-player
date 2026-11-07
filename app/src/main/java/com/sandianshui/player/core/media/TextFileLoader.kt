package com.sandianshui.player.core.media

import android.content.Context
import android.net.Uri
import com.sandianshui.player.core.subtitle.SubtitleDecoder
import com.sandianshui.player.core.subtitle.SubtitleEncoding

/** 通过 ContentResolver 读取字幕 / 歌词文本（SAF Uri）。 */
object TextFileLoader {

    /** 读取原始字节，便于按不同编码（含 Shift_JIS / GBK / UTF-16LE）解码；失败返回 null。 */
    fun readBytes(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
    }.getOrNull()

    /**
     * 按指定编码读取并解码文本，[SubtitleEncoding.AUTO] 时自动识别编码；失败返回 null。
     */
    fun readText(
        context: Context,
        uri: Uri,
        encoding: SubtitleEncoding = SubtitleEncoding.AUTO,
    ): String? = readBytes(context, uri)?.let { SubtitleDecoder.decode(it, encoding) }
}
