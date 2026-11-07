package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject

/**
 * DeepSeek Flash 批量翻译。
 *
 * 把全部句子编号后一次性发给模型（上下文连贯、费用低），返回按编号对齐的译文。
 * 超过 [BATCH_SIZE] 句时自动分批。
 *
 * 文档：https://api-docs.deepseek.com/api/create-chat-completion/
 */
class DeepSeekTranslator(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
) : TranslatorProvider {

    override val name: String = "DeepSeek Flash"

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
        // 翻译失败的句子保留原文
        return result.mapIndexed { i, v -> v ?: sentences[i] }
    }

    /** 翻译一批句子，返回与输入等长的译文列表。 */
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
            put("model", model)
            put("temperature", 0.3)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", numbered))
                },
            )
        }.toString()

        val (code, resp) = SimpleHttp.postJson(
            url = "$endpoint/chat/completions",
            jsonBody = body,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            readTimeoutMs = 120_000,
        )
        if (code !in 200..299 || resp == null) {
            throw TranslateException("DeepSeek 翻译失败（HTTP $code）：${resp?.take(200)}")
        }
        val content = JSONObject(resp)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: throw TranslateException("DeepSeek 响应缺少 choices[0].message.content")
        return parseNumberedResponse(content, sentences.size)
    }

    /**
     * 解析模型返回的编号译文，按编号对齐到输入顺序。
     * 纯函数，可单测。缺失编号的位置用空字符串占位（上层会回退到原文）。
     */
    fun parseNumberedResponse(content: String, expectedCount: Int): List<String> {
        val result = arrayOfNulls<String>(expectedCount)
        // 匹配 "1.译文" 或 "1、译文" 或 "1: 译文" 格式
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
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com"
        const val DEFAULT_MODEL = "deepseek-flash"
        const val BATCH_SIZE = 50
    }
}
