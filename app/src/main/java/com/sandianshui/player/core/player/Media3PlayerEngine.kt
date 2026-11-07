package com.sandianshui.player.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer

/**
 * 基于 AndroidX Media3（ExoPlayer）的播放内核实现。
 * mp3 / wav 原生支持，setPlaybackSpeed 可在 0.5x ~ 5x 流畅变速。
 */
class Media3PlayerEngine(context: Context) : PlayerEngine {

    private val player: ExoPlayer =
        ExoPlayer.Builder(context.applicationContext).build()

    override val isPlaying: Boolean
        get() = player.isPlaying

    override val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    override val durationMs: Long
        get() = if (player.duration == C.TIME_UNSET) 0L else player.duration.coerceAtLeast(0L)

    override val speed: Float
        get() = player.playbackParameters.speed

    override fun setMedia(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
        player.seekTo(0L)
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed, 1.0f)
    }

    override fun release() {
        player.release()
    }
}
