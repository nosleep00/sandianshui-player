package com.sandianshui.player.core.translate.model

/**
 * ASR 识别出的一句话：[beginMs] 到 [endMs] 毫秒区间内的 [text]（原文）。
 */
data class AsrSentence(
    val beginMs: Long,
    val endMs: Long,
    val text: String,
)
