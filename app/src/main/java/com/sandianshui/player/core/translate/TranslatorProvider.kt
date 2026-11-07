package com.sandianshui.player.core.translate

/**
 * 翻译提供方接口。当前实现为 DeepSeek Flash，后续可扩展 GPT、DeepL 等。
 */
interface TranslatorProvider {
    val name: String

    /**
     * 批量翻译 [sentences]，返回与输入等长、按顺序对齐的译文列表。
     * @param source 源语言代码，如 "ja"。
     * @param target 目标语言代码，如 "zh"。
     */
    suspend fun translate(
        sentences: List<String>,
        source: String = "ja",
        target: String = "zh",
    ): List<String>
}
