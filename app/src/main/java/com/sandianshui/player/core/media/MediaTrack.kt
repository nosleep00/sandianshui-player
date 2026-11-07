package com.sandianshui.player.core.media

import android.net.Uri
import com.sandianshui.player.core.subtitle.SubtitleFormat

/**
 * 一首已加载的音频及其自动 / 手动匹配到的配套资源。
 *
 * 字幕 / 歌词有两种来源：
 * - 本地：[subtitleUri]（SAF content Uri）；
 * - 在线：[subtitleTextUrl]（HTTP 文本流地址，播放时实时拉取，不下载音频本体）。
 */
data class MediaTrack(
    val audioUri: Uri,
    val displayName: String,
    val subtitleUri: Uri? = null,
    val subtitleFormat: SubtitleFormat? = null,
    val coverUri: Uri? = null,
    /** 在线字幕 / 歌词文本的 HTTP 地址；非空时优先于 [subtitleUri]。 */
    val subtitleTextUrl: String? = null,
)
