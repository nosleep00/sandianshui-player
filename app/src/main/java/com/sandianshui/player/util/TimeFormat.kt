package com.sandianshui.player.util

/** 毫秒格式化为 mm:ss（超过 1 小时也只显示分钟累加，符合本期单首音频场景）。 */
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
