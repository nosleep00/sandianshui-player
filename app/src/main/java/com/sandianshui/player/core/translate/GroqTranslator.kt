package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Groq 批量翻译（模型 openai/gpt-oss-20b）。
 *
 * Groq 提供 OpenAI 兼容接口，请求 / 响应格式与 DeepSeek 一致。
 *
 * 免费档限流（gpt-oss-20b）：30 RPM、8000 TPM、200K TPD。
 * 该模型是推理模型，默认会生成大量 reasoning token，单批很容易撑爆 8000 TPM。
 * 因此本实现：
 * 1. 小批次（[BATCH_SIZE] 句一批），单批 token 远低于 8000；
 * 2. 批次之间固定等待 [BATCH_INTERVAL_MS]，平滑每分钟 token；
 * 3. 遇到 429 按 [RETRY_BACKOFF_MS] 递增退避重试（限流按分钟计算）。
 *
 * 上下文能力（[contextLevel]，5 档）：
 * - 档位决定 reasoning_effort（low/medium/high）与每批附带的前后文句数，
 *   参考上下文只用于理解语境、不要求翻译，从而让译文更自然、不生硬；
 * - 响应仍只含编号的目标译文，解析逻辑保持稳定。
 *
 * 漏译治理（长音频时部分句子仍是日文）：
 * 1. 顺序解析器全局扫描编号，容忍同排、换行、各种分隔符，按 1..N 严格对齐；
 * 2. 解析后检测空句 / 仍含日文假名的句子（目标非日语）；
 * 3. 对这些句子进入"补齐"重译，最多 [MAX_REPAIR_ROUNDS] 轮。
 *
 * 注意：响应里同时有 message.content（最终译文）与 message.reasoning（思考过程），
 * 只解析 content。
 *
 * 文档：https://console.groq.com/docs/api-reference
 */
class GroqTranslator(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
    private val contextLevel: GroqContextLevel = GroqContextLevel.DEFAULT,
) : TranslatorProvider {

    override val name: String = "Groq gpt-oss-20b"

    override suspend fun translate(
        sentences: List<String>,
        source: String,
        target: String,
    ): List<String> {
        if (sentences.isEmpty()) return emptyList()
        val result = arrayOfNulls<String>(sentences.size)

        // 1. 常规分批翻译（传入完整句子列表与本批目标下标，便于取上下文）
        val chunks = sentences.indices.chunked(BATCH_SIZE)
        chunks.forEachIndexed { chunkIndex, targetIndices ->
            if (chunkIndex > 0) delay(BATCH_INTERVAL_MS)
            val translated = requestWithRetry(sentences, targetIndices, source, target, repair = false)
            for (i in translated.indices) {
                if (i < targetIndices.size) result[targetIndices[i]] = translated[i]
            }
        }

        // 2. 漏译补齐：找出空句 / 仍含日文假名的句子，重译
        repeat(MAX_REPAIR_ROUNDS) {
            val missing = sentences.indices.filter { isUntranslated(result[it], target) }
            if (missing.isEmpty()) return@repeat
            delay(BATCH_INTERVAL_MS)
            val repaired = requestWithRetry(sentences, missing, source, target, repair = true)
            for (j in missing.indices) {
                val candidate = repaired.getOrNull(j).orEmpty()
                if (candidate.isNotBlank()) {
                    // 新结果即使仍含假名，也优先于空句；已有中文结果且新句仍含假名则不覆盖
                    val current = result[missing[j]]
                    if (current.isNullOrBlank() || !isUntranslated(candidate, target)) {
                        result[missing[j]] = candidate
                    }
                }
            }
        }

        // 3. 仍缺失的句子保留原文，保证时间轴完整
        return result.mapIndexed { i, v -> if (v.isNullOrBlank()) sentences[i] else v }
    }

    /** 带 429 退避重试的批量翻译。重试次数用尽返回空串占位，避免整次翻译失败。 */
    private suspend fun requestWithRetry(
        allSentences: List<String>,
        targetIndices: List<Int>,
        source: String,
        target: String,
        repair: Boolean,
    ): List<String> {
        var lastError: String? = null
        repeat(MAX_RETRIES + 1) { attempt ->
            if (attempt > 0) delay(RETRY_BACKOFF_MS[attempt - 1])
            val outcome = tryRequest(allSentences, targetIndices, source, target, repair)
            when (outcome) {
                is BatchOutcome.Success -> return outcome.translations
                is BatchOutcome.RateLimited -> lastError = outcome.message
                is BatchOutcome.Error -> throw TranslateException(outcome.message)
            }
        }
        return targetIndices.map { "" }
    }

    private sealed class BatchOutcome {
        data class Success(val translations: List<String>) : BatchOutcome()
        data class RateLimited(val message: String) : BatchOutcome()
        data class Error(val message: String) : BatchOutcome()
    }

    /** 发起一次请求并分类结果。 */
    private fun tryRequest(
        allSentences: List<String>,
        targetIndices: List<Int>,
        source: String,
        target: String,
        repair: Boolean,
    ): BatchOutcome {
        val n = targetIndices.size
        val systemPrompt = buildString {
            if (repair) {
                append("你是专业的字幕翻译。下面 $n 句${sourceName(source)}在上一次处理中没有被翻译，")
                append("现在必须逐句完整翻译成${targetName(target)}，一句都不能保留原文、不能遗漏或合并。")
            } else {
                append("你是专业的字幕翻译。下面共有 $n 句${sourceName(source)}需要翻译，")
                append("请逐句完整翻译成${targetName(target)}，确保每一句都翻译，不得遗漏、合并或跳过。")
            }
            if (contextLevel.contextSentences > 0) {
                append("已提供参考上下文，请结合对话语境理解语气和指代，让译文自然流畅，不要逐字硬译；")
                append("参考上下文不需要翻译。")
            }
            append("保留口语风格和语气词，人名/专有名词可音译。")
            append("只输出需要翻译的句子，每行一句，格式为“序号.译文”，序号从 1 到 $n，不要加任何解释。")
        }

        val userContent = buildUserContent(allSentences, targetIndices)

        val body = JSONObject().apply {
            put("model", model)
            put("temperature", 0.3)
            put("stream", false)
            put("reasoning_effort", contextLevel.reasoningEffort)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userContent))
                },
            )
        }.toString()

        val (code, resp) = SimpleHttp.postJson(
            url = "$endpoint/chat/completions",
            jsonBody = body,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            readTimeoutMs = 120_000,
        )
        if (code == 429) {
            return BatchOutcome.RateLimited("HTTP 429：${resp?.take(160)}")
        }
        if (code !in 200..299 || resp == null) {
            return BatchOutcome.Error("Groq 翻译失败（HTTP $code）：${resp?.take(200)}")
        }
        val content = JSONObject(resp)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?: return BatchOutcome.Error("Groq 响应缺少 choices[0].message.content")
        return BatchOutcome.Success(parseNumberedResponse(content, n))
    }

    /**
     * 构建用户消息：先放参考上下文（不翻译），再放编号的目标译文。
     * 纯函数，可单测。
     */
    fun buildUserContent(allSentences: List<String>, targetIndices: List<Int>): String {
        val c = contextLevel.contextSentences
        val sb = StringBuilder()
        if (c > 0 && targetIndices.isNotEmpty()) {
            val first = targetIndices.first()
            val last = targetIndices.last()
            val before = ((first - c).coerceAtLeast(0) until first)
                .filter { it !in targetIndices }
            val after = ((last + 1)..(last + c).coerceAtMost(allSentences.lastIndex))
                .filter { it !in targetIndices }
            if (before.any() || after.any()) {
                sb.append("【参考上下文，无需翻译】\n")
                before.forEach { sb.append("（前文）").append(allSentences[it]).append('\n') }
                after.forEach { sb.append("（后文）").append(allSentences[it]).append('\n') }
                sb.append('\n')
            }
        }
        sb.append("【需要翻译的句子，共 ").append(targetIndices.size).append(" 句】\n")
        targetIndices.forEachIndexed { k, idx ->
            sb.append(k + 1).append('.').append(allSentences[idx])
            if (k < targetIndices.size - 1) sb.append('\n')
        }
        return sb.toString()
    }

    /**
     * 顺序解析模型返回的编号译文，按 1..[expectedCount] 严格对齐。
     *
     * 全局扫描编号标记（不要求编号在行首），按期望序号逐个接受，因此：
     * - 同一行出现多条也能识别；
     * - 长句换行的续行归入上一条；
     * - 译文里偶然出现的数字（不符合期望序号）不会误判。
     *
     * 纯函数，可单测。缺失编号的位置用空字符串占位。
     */
    fun parseNumberedResponse(content: String, expectedCount: Int): List<String> {
        val result = arrayOfNulls<String>(expectedCount)
        if (expectedCount <= 0) return emptyList()
        val markerRegex = Regex("""(\d{1,3})\s*[\.、:：)）】\]》\-–]\s*""")
        var expected = 1
        var lastAcceptedNum = 0
        var contentStart = -1
        for (m in markerRegex.findAll(content)) {
            val num = m.groupValues[1].toInt()
            val atLineStart = isAtLineStart(content, m.range.first)
            // 行内编号必须严格等于期望序号；行首编号允许向前跳号（中间条目视为缺失）
            val accept = when {
                num == expected && num <= expectedCount -> true
                atLineStart && num > expected && num <= expected + JUMP_TOLERANCE && num <= expectedCount -> true
                else -> false
            }
            if (accept) {
                if (contentStart >= 0 && lastAcceptedNum in 1..expectedCount) {
                    val prevIdx = lastAcceptedNum - 1
                    val text = content.substring(contentStart, m.range.first).trim()
                    if (result[prevIdx] == null) result[prevIdx] = text
                }
                contentStart = m.range.last + 1
                lastAcceptedNum = num
                expected = num + 1
            }
        }
        if (contentStart >= 0 && lastAcceptedNum in 1..expectedCount) {
            val lastIdx = lastAcceptedNum - 1
            if (result[lastIdx] == null) {
                result[lastIdx] = content.substring(contentStart).trim()
            }
        }
        return result.map { it ?: "" }
    }

    /** 判断 [pos] 处是否位于一行的开头（该行之前只有空白字符）。 */
    private fun isAtLineStart(content: String, pos: Int): Boolean {
        var i = pos - 1
        while (i >= 0) {
            val ch = content[i]
            if (ch == '\n') return true
            if (ch != ' ' && ch != '\t' && ch != '\r') return false
            i--
        }
        return true
    }

    /**
     * 判断某句是否"未翻译"：空串，或目标语言非日语时仍含 2 个及以上日文假名。
     * 纯函数，可单测。
     */
    fun isUntranslated(text: String?, target: String): Boolean {
        if (text.isNullOrBlank()) return true
        if (target == "ja") return false
        val kanaCount = KANA_REGEX.findAll(text).count()
        return kanaCount >= 2
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
        const val DEFAULT_ENDPOINT = "https://api.groq.com/openai/v1"
        const val DEFAULT_MODEL = "openai/gpt-oss-20b"

        /** 每批句子数。小批次保证单批 token 远低于 8000 TPM。 */
        const val BATCH_SIZE = 10

        /** 批次之间的等待时间（毫秒），用于平滑每分钟 token。 */
        const val BATCH_INTERVAL_MS = 9_000L

        /** 429 后每次重试的等待时间（毫秒），递增；长度即重试次数。 */
        val RETRY_BACKOFF_MS = longArrayOf(20_000L, 40_000L, 60_000L)

        /** 429 的最大重试次数（不含首次请求）。 */
        const val MAX_RETRIES = 3

        /** 漏译补齐的最大轮数。 */
        const val MAX_REPAIR_ROUNDS = 2

        /** 行首编号允许向前跳号的最大跨度。 */
        const val JUMP_TOLERANCE = 3

        /** 日文假名（平假名 + 片假名）。 */
        val KANA_REGEX = Regex("[\\u3040-\\u30ff]")
    }
}
