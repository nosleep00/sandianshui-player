package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini 批量翻译。
 *
 * 使用原生 generateContent 接口，认证头用 x-goog-api-key（同时兼容
 * 旧版 AIza 流量 Key 与新版 AQ 认证 Key）。
 * 编号对齐、分批策略与 [DeepSeekTranslator] 一致。
 *
 * 文档：https://ai.google.dev/gemini-api/docs/text-generation
 */
class GeminiTranslator(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
) : TranslatorProvider {

    override val name: String = "Gemini"

    override suspend fun translate(
        sentences: List<String>,
        source: String,
        target: String,
    ): List<String> {
        if (sentences.isEmpty()) return emptyList()
        val result = arrayOfNulls<String>(sentences.size)
        sentences.withIndex().chunked(BATCH_SIZE).forEach { chunk ->
            val indices = chunk.map { it.index }
            val texts = chunk.map { it.value }
            val translated = translateBatch(texts, source, target)
            for (i in translated.indices) {
                if (i < indices.size) result[indices[i]] = translated[i]
            }
        }
        return result.mapIndexed { i, v -> v ?: sentences[i] }
    }

    private fun translateBatch(
        sentences: List<String>,
        source: String,
        target: String,
    ): List<String> {
        val numbered = sentences.withIndex().joinToString("\n") { "${it.index + 1}.${it.value}" }
        val systemPrompt = buildString {
            append("你是专业的字幕翻译。将下面编号的${sourceName(source)}句子翻译成${targetName(target)}。")
            append("保留口语风格和语气词，人名/专有名词不译。")
            append("输出格式：每行一个编号，编号后紧跟译文，不要加解释或其他内容。")
        }
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().put("text", systemPrompt))
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", numbered))
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
            })
        }.toString()

        val url = "$endpoint/models/$model:generateContent"
        val (code, resp) = SimpleHttp.postJson(
            url = url,
            jsonBody = body,
            headers = mapOf("x-goog-api-key" to apiKey),
            readTimeoutMs = 120_000,
        )
        if (code !in 200..299 || resp == null) {
            throw TranslateException("Gemini 翻译失败（HTTP $code）：${resp?.take(200)}")
        }
        val content = JSONObject(resp)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: throw TranslateException("Gemini 响应缺少 candidates[0].content.parts[0].text")
        return parseNumberedResponse(content, sentences.size)
    }

    /** 解析编号译文，按编号对齐。纯函数，可单测。 */
    fun parseNumberedResponse(content: String, expectedCount: Int): List<String> {
        val result = arrayOfNulls<String>(expectedCount)
        val regex = Regex("""(?:^|\n)\s*(\d{1,4})[\.、:：]\s*(.+?)(?=\n\s*\d{1,4}[\.、:：]|$)""")
        for (m in regex.findAll(content)) {
            val num = m.groupValues[1].toIntOrNull() ?: continue
            val text = m.groupValues[2].trim()
            val idx = num - 1
            if (idx in 0 until expectedCount && result[idx] == null) {
                result[idx] = text
            }
        }
        return result.map { it ?: "" }
    }

    private fun sourceName(code: String): String = when (code) {
        "ja" -> "日语"
        "en" -> "英语"
        "zh" -> "中文"
        else -> code
    }

    private fun targetName(code: String): String = when (code) {
        "zh" -> "简体中文"
        "en" -> "英语"
        "ja" -> "日语"
        else -> code
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val BATCH_SIZE = 50
    }
}
