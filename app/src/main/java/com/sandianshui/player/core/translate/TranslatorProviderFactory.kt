package com.sandianshui.player.core.translate

/**
 * 根据服务商类型与凭据创建 [TranslatorProvider]。
 *
 * 多数服务商只需一个 [apiKey]；Lara 需要 Access Key ID（[apiKey]）
 * 与 Access Key Secret（[apiKey2]）。
 */
object TranslatorProviderFactory {
    fun create(
        type: TranslatorProviderType,
        apiKey: String,
        apiKey2: String = "",
        groqContextLevel: GroqContextLevel = GroqContextLevel.DEFAULT,
    ): TranslatorProvider = when (type) {
        TranslatorProviderType.DEEPSEEK -> DeepSeekTranslator(apiKey)
        TranslatorProviderType.GEMINI -> GeminiTranslator(apiKey)
        TranslatorProviderType.LARA -> LaraTranslator(
            accessKeyId = apiKey,
            accessKeySecret = apiKey2,
        )
        TranslatorProviderType.GROQ -> GroqTranslator(
            apiKey = apiKey,
            contextLevel = groqContextLevel,
        )
    }
}
