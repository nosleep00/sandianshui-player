package com.sandianshui.player.ui.player

import android.net.Uri
import com.sandianshui.player.core.remote.AsmrTrack
import com.sandianshui.player.core.remote.AsmrWork
import com.sandianshui.player.core.subtitle.Cue
import com.sandianshui.player.core.subtitle.SubtitleEncoding

/** 大卡片的两种视图：封面 / 字幕歌词时间轴。 */
enum class CardMode { COVER, TIMELINE }

/**
 * 播放界面的完整状态。界面是该状态的函数。
 */
data class PlayerUiState(
    val trackName: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
    /** 同一文件夹播放列表中的切换可用性与序号（用于上一首 / 下一首按钮）。 */
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val trackCount: Int = 0,
    val currentTrackIndex: Int = -1,
    val cues: List<Cue> = emptyList(),
    val activeCueIndex: Int = -1,
    /** 可拖动悬浮框实时显示的当前句；句间 / 结束后保留上一句，不置空。 */
    val floatingText: String? = null,
    val cardMode: CardMode = CardMode.COVER,
    val coverUri: Uri? = null,
    val contentLabel: String = "暂无歌词 · 暂无字幕",
    /** 当前曲目是否带字幕 / 歌词（决定右下角编码切换按钮是否显示）。 */
    val hasSubtitle: Boolean = false,
    /** 字幕 / 歌词当前使用的文本编码（自动或用户手动指定）。 */
    val encoding: SubtitleEncoding = SubtitleEncoding.AUTO,
    /** 在线搜索：ASMR.one 结果与加载状态。 */
    val isSearchingAsmr: Boolean = false,
    val asmrResults: List<AsmrWork> = emptyList(),
    /** 最近一次搜索的关键词（翻页时复用）；未搜索过为 null。 */
    val asmrKeyword: String? = null,
    /** 当前页码（从 1）与总页数、总条数，用于分页栏。 */
    val asmrCurrentPage: Int = 1,
    val asmrTotalPages: Int = 1,
    val asmrTotalCount: Int = 0,
    /** 点击某作品后、正在拉取该作品音轨。 */
    val isLoadingTracks: Boolean = false,
    /** 当前展开的作品及其全部音轨（点击作品后展示，点具体音轨才播放）。 */
    val selectedWork: AsmrWork? = null,
    val workTracks: List<AsmrTrack> = emptyList(),
    /** 一次性提示消息，UI 展示后调用 consumeMessage 清空。 */
    val message: String? = null,
    /** AI 翻译生成字幕的进度状态。 */
    val translationState: TranslationUiState = TranslationUiState.Idle,
    /** 是否显示 AI 翻译 API Key 配置对话框。 */
    val showTranslateConfig: Boolean = false,
    /** 是否显示在线音源接口地址配置对话框。 */
    val showSourceConfig: Boolean = false,
) {
    val hasTrack: Boolean get() = trackName != null
}

/** AI 翻译生成字幕的 UI 状态。 */
sealed class TranslationUiState {
    object Idle : TranslationUiState()
    data class AsrRunning(val elapsedSeconds: Int) : TranslationUiState()
    data class Translating(val estimatedSeconds: Int? = null) : TranslationUiState()
    object Done : TranslationUiState()
    data class Error(val message: String) : TranslationUiState()
}

/** 倍速滑杆范围，最高 5x。 */
const val MIN_SPEED: Float = 0.5f
const val MAX_SPEED: Float = 5.0f

/** 把倍速限制在 [MIN_SPEED, MAX_SPEED] 内（拖动越界时兜底）。 */
fun coerceSpeed(speed: Float): Float = speed.coerceIn(MIN_SPEED, MAX_SPEED)

/** 倍速文案，如 1.0 -> "1.0x"。 */
fun speedLabel(speed: Float): String = String.format("%.1f", speed) + "x"

/** 把秒数转成"X 分 Y 秒"或"X 秒"的可读文案。 */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "不到 1 秒"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m} 分 ${s} 秒" else "$s 秒"
}
