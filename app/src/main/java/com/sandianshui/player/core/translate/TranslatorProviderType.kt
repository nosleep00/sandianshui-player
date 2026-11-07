package com.sandianshui.player.core.translate

/**
 * 文字翻译服务商类型。
 *
 * 新增服务商时：添加一个枚举值，并在 [TranslatorProviderFactory] 中补充创建逻辑。
 */
enum class TranslatorProviderType(val id: String, val displayName: String) {
    DEEPSEEK("deepseek", "DeepSeek Flash"),
    GEMINI("gemini", "Google Gemini"),
    LARA("lara", "Lara Translate"),
    GROQ("groq", "Groq gpt-oss-20b"),
    ;

    companion object {
        fun fromId(id: String): TranslatorProviderType =
            entries.find { it.id == id } ?: DEEPSEEK
    }
}
