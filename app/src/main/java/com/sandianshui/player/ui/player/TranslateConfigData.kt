package com.sandianshui.player.ui.player

import com.sandianshui.player.core.translate.AsrProviderType
import com.sandianshui.player.core.translate.GroqContextLevel
import com.sandianshui.player.core.translate.TranslatorProviderType

/**
 * AI 翻译配置对话框的完整数据。
 *
 * @param asrTypes 可选的语音识别服务商列表。
 * @param selectedAsr 当前选中的语音识别服务商。
 * @param asrKeys 各语音识别服务商已保存的 Key（含未配置的，值为空串）。
 * @param translatorTypes 可选的翻译服务商列表。
 * @param selectedTranslator 当前选中的翻译服务商。
 * @param translatorKeys 各翻译服务商已保存的主凭据（Lara 为 Access Key ID）。
 * @param translatorKeys2 各翻译服务商的第二凭据（Lara 为 Access Key Secret）。
 */
data class TranslateConfigData(
    val asrTypes: List<AsrProviderType>,
    val selectedAsr: AsrProviderType,
    val asrKeys: Map<AsrProviderType, String>,
    val translatorTypes: List<TranslatorProviderType>,
    val selectedTranslator: TranslatorProviderType,
    val translatorKeys: Map<TranslatorProviderType, String>,
    val translatorKeys2: Map<TranslatorProviderType, String> = emptyMap(),
    val groqContextLevel: GroqContextLevel = GroqContextLevel.DEFAULT,
)
