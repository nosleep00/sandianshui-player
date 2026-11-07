package com.sandianshui.player.core.ext

import android.net.Uri

/**
 * 在线播放源（扩展预留，本期不实现）。
 * 未来把在线资源 id 解析成可播放的 Uri 后，可直接交给 PlayerEngine 播放。
 */
interface OnlineSource {
    val id: String
    val displayName: String

    /** 解析在线资源；失败或不支持时返回 null。 */
    suspend fun resolve(mediaId: String): Uri?
}

/** 搜索到的在线媒体条目。 */
data class OnlineMedia(
    val id: String,
    val title: String,
    val sourceId: String,
)
