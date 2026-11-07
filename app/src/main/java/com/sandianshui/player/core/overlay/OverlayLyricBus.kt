package com.sandianshui.player.core.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮窗当前要显示的内容。
 * @param text 当前句文本；尚未有句子时为 null（界面显示占位「—」）。
 * @param hasCues 当前曲目是否带字幕 / 歌词。
 */
data class OverlayLyricContent(
    val text: String? = null,
    val hasCues: Boolean = false,
)

/**
 * App 内播放状态 与 系统悬浮窗服务之间的「总线」。
 *
 * ViewModel 在播放过程中把当前句写进这里；[OverlayLyricService] 启动后
 * 观察它，即使播放器界面已切到后台、用户在别的 App，悬浮窗也能实时更新。
 * 用进程内单例即可（悬浮窗与播放器同进程）。
 */
object OverlayLyricBus {

    private val _content = MutableStateFlow(OverlayLyricContent())
    val content: StateFlow<OverlayLyricContent> = _content.asStateFlow()

    fun update(text: String?, hasCues: Boolean) {
        _content.value = OverlayLyricContent(text, hasCues)
    }

    fun reset() {
        _content.value = OverlayLyricContent()
    }
}
