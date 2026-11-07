package com.sandianshui.player.core.player

import android.net.Uri

/**
 * 播放内核抽象。UI / ViewModel 只依赖该接口，不直接依赖 ExoPlayer，
 * 便于替换内核，也为未来在线播放预留。
 */
interface PlayerEngine {

    val isPlaying: Boolean

    /** 当前播放位置（毫秒）。 */
    val currentPositionMs: Long

    /** 总时长（毫秒）；尚未就绪时返回 0。 */
    val durationMs: Long

    /** 当前倍速。 */
    val speed: Float

    fun setMedia(uri: Uri)

    fun play()

    fun pause()

    /** 跳转到指定位置（毫秒）。 */
    fun seekTo(positionMs: Long)

    /** 设置倍速，建议范围 0.5f ~ 5.0f。 */
    fun setSpeed(speed: Float)

    fun release()
}
