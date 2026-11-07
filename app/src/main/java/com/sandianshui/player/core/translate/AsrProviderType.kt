package com.sandianshui.player.core.translate

/**
 * 语音识别（ASR）服务商类型。
 *
 * 新增服务商时：添加一个枚举值，并在 [AsrProviderFactory] 中补充创建逻辑。
 */
enum class AsrProviderType(val id: String, val displayName: String) {
    BAILIAN("bailian", "阿里百炼 Paraformer"),
    ELEVENLABS("elevenlabs", "ElevenLabs Scribe"),
    ASSEMBLYAI("assemblyai", "AssemblyAI"),
    ;

    companion object {
        fun fromId(id: String): AsrProviderType =
            entries.find { it.id == id } ?: BAILIAN
    }
}
