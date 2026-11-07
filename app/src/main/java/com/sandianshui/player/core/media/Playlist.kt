package com.sandianshui.player.core.media

/**
 * 同一文件夹内的音频播放列表（纯 Kotlin、不依赖 Android，便于单元测试）。
 *
 * 维护有序曲目与当前下标，提供上一首 / 下一首切换；到两端时不可继续切换
 * （[canPrevious] / [canNext] 为 false），不做循环跳转。
 */
class Playlist<T>(initial: List<T> = emptyList()) {

    private var items: List<T> = initial

    var currentIndex: Int = 0
        private set

    val size: Int get() = items.size

    val current: T? get() = items.getOrNull(currentIndex)

    /** 是否存在上一首（当前不是第一首）。 */
    val canPrevious: Boolean get() = currentIndex > 0

    /** 是否存在下一首（当前不是最后一首）。 */
    val canNext: Boolean get() = currentIndex < size - 1

    /** 重新装入曲目并定位到 [index]（越界自动夹到合法范围；空列表归零）。 */
    fun set(newItems: List<T>, index: Int = 0) {
        items = newItems
        currentIndex = if (newItems.isEmpty()) 0 else index.coerceIn(0, newItems.size - 1)
    }

    /** 切到上一首；已在第一首时保持不变。返回切换后的当前曲目。 */
    fun previous(): T? {
        if (canPrevious) currentIndex -= 1
        return current
    }

    /** 切到下一首；已在最后一首时保持不变。返回切换后的当前曲目。 */
    fun next(): T? {
        if (canNext) currentIndex += 1
        return current
    }

    /** 更新当前曲目（如手动指定字幕 / 封面后），切换走再切回来仍保留。 */
    fun updateCurrent(item: T) {
        if (currentIndex in items.indices) {
            items = items.toMutableList().also { it[currentIndex] = item }
        }
    }
}
