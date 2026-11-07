package com.sandianshui.player.core.translate

/**
 * 根据服务商类型与 API Key 创建 [AsrProvider]。
 */
object AsrProviderFactory {
    fun create(type: AsrProviderType, apiKey: String): AsrProvider = when (type) {
        AsrProviderType.BAILIAN -> BailianAsrProvider(apiKey)
        AsrProviderType.ELEVENLABS -> ElevenLabsScribeProvider(apiKey)
        AsrProviderType.ASSEMBLYAI -> AssemblyAiProvider(apiKey)
    }
}
