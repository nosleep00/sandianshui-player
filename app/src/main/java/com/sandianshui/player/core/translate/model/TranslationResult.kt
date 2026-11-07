package com.sandianshui.player.core.translate.model

/**
 * 翻译管线的输出：生成的 [srtText]（可直接喂给现有 SrtParser），
 * 以及保留的 [originalSentences]（原文，用于双语对照或调试）。
 */
data class TranslationResult(
    val srtText: String,
    val originalSentences: List<AsrSentence>,
)
