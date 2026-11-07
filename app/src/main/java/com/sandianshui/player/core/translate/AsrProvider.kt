package com.sandianshui.player.core.translate

import com.sandianshui.player.core.translate.model.AsrSentence

/**
 * 语音识别提供方接口。当前实现为阿里百炼 paraformer-v2，
 * 后续可扩展 OpenAI Whisper、Google Speech 等。
 */
interface AsrProvider {
    val name: String

    /**
     * 对公网可访问的 [audioUrl] 进行语音识别，返回带时间戳的句子列表。
     * @param language 语言代码，如 "ja"（日语）、"zh"（中文）、"en"（英文）。
     * @param onElapsed 已等待秒数回调（用于 UI 进度）。
     */
    suspend fun transcribe(
        audioUrl: String,
        language: String = "ja",
        onElapsed: (Int) -> Unit = {},
    ): List<AsrSentence>
}
