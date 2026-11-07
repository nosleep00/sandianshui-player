package com.sandianshui.player.core.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 服务商类型枚举与工厂的单元测试。
 */
class ProviderTypeTest {

    // ===== ASR 类型 =====

    @Test
    fun `ASR类型 id 往返一致`() {
        for (type in AsrProviderType.entries) {
            assertEquals(type, AsrProviderType.fromId(type.id))
        }
    }

    @Test
    fun `ASR类型 未知 id 回退到默认百炼`() {
        assertEquals(AsrProviderType.BAILIAN, AsrProviderType.fromId("not-exist"))
    }

    @Test
    fun `ASR类型 百炼显示名正确`() {
        assertEquals("阿里百炼 Paraformer", AsrProviderType.BAILIAN.displayName)
    }

    // ===== 翻译类型 =====

    @Test
    fun `翻译类型 id 往返一致`() {
        for (type in TranslatorProviderType.entries) {
            assertEquals(type, TranslatorProviderType.fromId(type.id))
        }
    }

    @Test
    fun `翻译类型 未知 id 回退到默认 DeepSeek`() {
        assertEquals(TranslatorProviderType.DEEPSEEK, TranslatorProviderType.fromId("nope"))
    }

    @Test
    fun `翻译类型 DeepSeek 显示名正确`() {
        assertEquals("DeepSeek Flash", TranslatorProviderType.DEEPSEEK.displayName)
    }

    // ===== 工厂 =====

    @Test
    fun `ASR工厂 百炼创建出对应实现`() {
        val provider = AsrProviderFactory.create(AsrProviderType.BAILIAN, "some-key")
        assertTrue(provider is BailianAsrProvider)
        assertEquals("阿里百炼 paraformer-v2", provider.name)
    }

    @Test
    fun `翻译工厂 DeepSeek 创建出对应实现`() {
        val provider = TranslatorProviderFactory.create(TranslatorProviderType.DEEPSEEK, "some-key")
        assertTrue(provider is DeepSeekTranslator)
        assertEquals("DeepSeek Flash", provider.name)
    }

    @Test
    fun `ASR工厂 AssemblyAI 创建出对应实现`() {
        val provider = AsrProviderFactory.create(AsrProviderType.ASSEMBLYAI, "some-key")
        assertTrue(provider is AssemblyAiProvider)
        assertEquals("AssemblyAI", provider.name)
    }

    @Test
    fun `翻译工厂 Lara 用 ID 与 Secret 创建`() {
        val provider = TranslatorProviderFactory.create(
            TranslatorProviderType.LARA,
            apiKey = "access-id",
            apiKey2 = "access-secret",
        )
        assertTrue(provider is LaraTranslator)
        assertEquals("Lara Translate", provider.name)
    }

    @Test
    fun `翻译工厂 Groq 创建出对应实现`() {
        val provider = TranslatorProviderFactory.create(TranslatorProviderType.GROQ, "some-key")
        assertTrue(provider is GroqTranslator)
        assertEquals("Groq gpt-oss-20b", provider.name)
    }

    @Test
    fun `新服务商显示名正确`() {
        assertEquals("AssemblyAI", AsrProviderType.ASSEMBLYAI.displayName)
        assertEquals("Lara Translate", TranslatorProviderType.LARA.displayName)
        assertEquals("Groq gpt-oss-20b", TranslatorProviderType.GROQ.displayName)
    }
}
