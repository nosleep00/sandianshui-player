package com.sandianshui.player.core.translate

import android.content.Context
import android.content.SharedPreferences

/**
 * AI 翻译配置存储。
 *
 * 支持多个 ASR / 翻译服务商：
 * - 每个服务商的 API Key 独立保存，切换服务商时不会丢失，无需反复输入。
 * - 记录当前选中的服务商。
 *
 * 使用 SharedPreferences（MODE_PRIVATE），Key 仅保存在用户本机，不会随应用分发或上传。
 */
class TranslateConfigStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ===== ASR 语音识别 =====

    /** 返回所有已保存 Key 的 ASR 服务商及其 Key（未配置的不包含）。 */
    fun getAllAsrKeys(): Map<AsrProviderType, String> {
        val map = linkedMapOf<AsrProviderType, String>()
        for (type in AsrProviderType.entries) {
            val key = getAsrKey(type)
            if (key.isNotBlank()) map[type] = key
        }
        return map
    }

    fun getAsrKey(type: AsrProviderType): String =
        prefs.getString(asrKeyPref(type), "")?.trim().orEmpty()

    fun setAsrKey(type: AsrProviderType, key: String) {
        prefs.edit().putString(asrKeyPref(type), key.trim()).apply()
    }

    var selectedAsr: AsrProviderType
        get() = AsrProviderType.fromId(
            prefs.getString(KEY_SELECTED_ASR, AsrProviderType.BAILIAN.id)
                ?: AsrProviderType.BAILIAN.id,
        )
        set(value) = prefs.edit().putString(KEY_SELECTED_ASR, value.id).apply()

    // ===== 文字翻译 =====

    /** 返回所有已保存 Key 的翻译服务商及其 Key。 */
    fun getAllTranslatorKeys(): Map<TranslatorProviderType, String> {
        val map = linkedMapOf<TranslatorProviderType, String>()
        for (type in TranslatorProviderType.entries) {
            val key = getTranslatorKey(type)
            if (key.isNotBlank()) map[type] = key
        }
        return map
    }

    fun getTranslatorKey(type: TranslatorProviderType): String =
        prefs.getString(translatorKeyPref(type), "")?.trim().orEmpty()

    fun setTranslatorKey(type: TranslatorProviderType, key: String) {
        prefs.edit().putString(translatorKeyPref(type), key.trim()).apply()
    }

    /**
     * 第二凭据（如 Lara 的 Access Key Secret）。多数服务商为空。
     * 主凭据 [getTranslatorKey] 对 Lara 存的是 Access Key ID。
     */
    fun getTranslatorKey2(type: TranslatorProviderType): String =
        prefs.getString(translatorKey2Pref(type), "")?.trim().orEmpty()

    fun setTranslatorKey2(type: TranslatorProviderType, key: String) {
        prefs.edit().putString(translatorKey2Pref(type), key.trim()).apply()
    }

    var selectedTranslator: TranslatorProviderType
        get() = TranslatorProviderType.fromId(
            prefs.getString(KEY_SELECTED_TRANSLATOR, TranslatorProviderType.DEEPSEEK.id)
                ?: TranslatorProviderType.DEEPSEEK.id,
        )
        set(value) = prefs.edit().putString(KEY_SELECTED_TRANSLATOR, value.id).apply()

    /** Groq 上下文能力档位（1..5），仅 Groq 使用，其它服务商忽略。 */
    var groqContextLevel: GroqContextLevel
        get() = GroqContextLevel.fromLevel(
            prefs.getInt(KEY_GROQ_CONTEXT_LEVEL, GroqContextLevel.DEFAULT.level),
        )
        set(value) = prefs.edit().putInt(KEY_GROQ_CONTEXT_LEVEL, value.level).apply()

    // ===== 便捷方法 =====

    /** 当前选中的 ASR 服务商的 Key。 */
    val currentAsrKey: String get() = getAsrKey(selectedAsr)

    /** 当前选中的翻译服务商的 Key。 */
    val currentTranslatorKey: String get() = getTranslatorKey(selectedTranslator)

    /** 当前选中的两个服务商的 Key 都已配置才视为可用。 */
    val isConfigured: Boolean
        get() = currentAsrKey.isNotBlank() && currentTranslatorKey.isNotBlank() &&
            currentTranslatorKey2.isBlankOrNotRequired()

    /** Lara 必须同时配置 ID（主）与 Secret（次）；其它服务商无第二凭据要求。 */
    private fun String?.isBlankOrNotRequired(): Boolean {
        if (selectedTranslator != TranslatorProviderType.LARA) return true
        return !this.isNullOrBlank()
    }

    /** 当前选中翻译服务商的第二凭据。 */
    val currentTranslatorKey2: String get() = getTranslatorKey2(selectedTranslator)

    /**
     * 一次性保存配置：选中的服务商、以及各服务商当前编辑的 Key。
     * @param asrKeyMap 各 ASR 服务商的 Key（可包含空值，表示清空）。
     * @param translatorKeyMap 各翻译服务商的主凭据（Lara 为 Access Key ID）。
     * @param translatorKey2Map 各翻译服务商的第二凭据（Lara 为 Access Key Secret）。
     */
    fun saveAll(
        selectedAsrType: AsrProviderType,
        asrKeyMap: Map<AsrProviderType, String>,
        selectedTranslatorType: TranslatorProviderType,
        translatorKeyMap: Map<TranslatorProviderType, String>,
        translatorKey2Map: Map<TranslatorProviderType, String> = emptyMap(),
        groqContextLevel: GroqContextLevel = this.groqContextLevel,
    ) {
        val editor = prefs.edit()
        asrKeyMap.forEach { (type, key) -> editor.putString(asrKeyPref(type), key.trim()) }
        translatorKeyMap.forEach { (type, key) -> editor.putString(translatorKeyPref(type), key.trim()) }
        translatorKey2Map.forEach { (type, key) -> editor.putString(translatorKey2Pref(type), key.trim()) }
        editor.putString(KEY_SELECTED_ASR, selectedAsrType.id)
        editor.putString(KEY_SELECTED_TRANSLATOR, selectedTranslatorType.id)
        editor.putInt(KEY_GROQ_CONTEXT_LEVEL, groqContextLevel.level)
        editor.apply()
    }

    private fun asrKeyPref(type: AsrProviderType) = "asr_key_${type.id}"
    private fun translatorKeyPref(type: TranslatorProviderType) = "translator_key_${type.id}"
    private fun translatorKey2Pref(type: TranslatorProviderType) = "translator_key2_${type.id}"

    companion object {
        private const val PREFS_NAME = "translate_config"
        private const val KEY_SELECTED_ASR = "selected_asr"
        private const val KEY_SELECTED_TRANSLATOR = "selected_translator"
        private const val KEY_GROQ_CONTEXT_LEVEL = "groq_context_level"
    }
}
