package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import com.sandianshui.player.core.translate.model.AsrSentence
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * AssemblyAI 语音识别（异步）。
 *
 * 流程：提交音频（POST /v2/transcript）→ 轮询转写状态（GET /v2/transcript/{id}）
 * → 完成后读取词级时间戳（words）并聚合为句子。
 *
 * 认证：请求头 `Authorization: <apiKey>`（**裸 Key，不加 Bearer 前缀**）。
 * 仅接受公网可访问的音频 URL（服务器侧拉取）。
 *
 * 注意：words 的 start / end 单位是**毫秒**（与 ElevenLabs 的秒不同）。
 *
 * 文档：https://www.assemblyai.com/docs/api-reference/transcripts/submit
 */
class AssemblyAiProvider(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : AsrProvider {

    override val name: String = "AssemblyAI"

    override suspend fun transcribe(
        audioUrl: String,
        language: String,
        onElapsed: (Int) -> Unit,
    ): List<AsrSentence> {
        onElapsed(1)
        val transcriptId = submit(audioUrl, language)
        onElapsed(2)
        val completed = poll(transcriptId, onElapsed)
        return parseSentences(completed)
    }

    /** 提交转写任务，返回 transcript id。 */
    private fun submit(audioUrl: String, language: String): String {
        val body = JSONObject().apply {
            put("audio_url", audioUrl)
            put("language_code", language)
        }.toString()
        val (code, resp) = SimpleHttp.postJson(
            url = endpoint,
            jsonBody = body,
            headers = mapOf("Authorization" to apiKey),
        )
        if (code !in 200..299 || resp == null) {
            throw TranslateException("AssemblyAI 提交失败（HTTP $code）：${resp?.take(200)}")
        }
        val id = JSONObject(resp).optString("id")
        if (id.isNullOrBlank()) {
            throw TranslateException("AssemblyAI 响应缺少 id：${resp.take(200)}")
        }
        return id
    }

    /** 轮询直到 completed / error，返回完成态 JSON。 */
    private suspend fun poll(id: String, onElapsed: (Int) -> Unit): String {
        val start = System.currentTimeMillis()
        val maxWaitMs = 10 * 60 * 1000L
        while (true) {
            val elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
            onElapsed(elapsed)
            if (elapsed * 1000L > maxWaitMs) {
                throw TranslateException("AssemblyAI 转写超过 10 分钟，请稍后重试")
            }
            val (code, resp) = SimpleHttp.get(
                url = "$endpoint/$id",
                userAgent = UA,
                headers = mapOf("Authorization" to apiKey),
                readTimeoutMs = 30_000,
            )
            if (code !in 200..299 || resp.isBlank()) {
                delay(3000)
                continue
            }
            when (JSONObject(resp).optString("status")) {
                "completed" -> return resp
                "error" -> throw TranslateException(
                    "AssemblyAI 转写失败：${JSONObject(resp).optString("error")}",
                )
                else -> delay(3000) // queued / processing
            }
        }
    }

    /**
     * 把完成态 JSON 的 words（毫秒时间戳）聚合为句子。
     * 纯函数，可单测。
     */
    fun parseSentences(json: String): List<AsrSentence> {
        val obj = JSONObject(json)
        val words = obj.optJSONArray("words") ?: return emptyList()
        data class Word(val start: Long, val end: Long, val text: String)

        val parsed = ArrayList<Word>(words.length())
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            val text = w.optString("text", "").trim()
            if (text.isEmpty()) continue
            val start = w.optLong("start", 0L)
            parsed.add(
                Word(
                    start = start,
                    end = w.optLong("end", start),
                    text = text,
                ),
            )
        }
        if (parsed.isEmpty()) return emptyList()

        val sentences = ArrayList<AsrSentence>()
        val buffer = StringBuilder()
        var segStart = parsed[0].start
        var segEnd = parsed[0].end

        fun flush() {
            val t = buffer.toString().trim()
            if (t.isNotEmpty()) {
                sentences.add(AsrSentence(beginMs = segStart, endMs = segEnd, text = t))
            }
            buffer.setLength(0)
        }

        for (i in parsed.indices) {
            val w = parsed[i]
            if (buffer.isEmpty()) segStart = w.start
            if (buffer.isNotEmpty() && needsSpaceBefore(buffer, w.text)) buffer.append(' ')
            buffer.append(w.text)
            segEnd = w.end

            val endsSentence = SENTENCE_ENDINGS.any { w.text.endsWith(it) }
            val next = parsed.getOrNull(i + 1)
            val longPause = next != null && (next.start - w.end) >= SILENCE_GAP_MS
            if (endsSentence || longPause) {
                flush()
                if (next != null) segStart = next.start
            }
        }
        flush()
        return sentences
    }

    private fun needsSpaceBefore(buffer: StringBuilder, next: String): Boolean {
        val last = buffer.lastOrNull() ?: return false
        val first = next.firstOrNull() ?: return false
        return last.isLatinLike() && first.isLatinLike()
    }

    private fun Char.isLatinLike(): Boolean =
        this.code in 0x41..0x7A || this == '\'' || this == '-'

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.assemblyai.com/v2/transcript"
        private const val UA = "SandianShuiPlayer/1.0"

        private val SENTENCE_ENDINGS = listOf("。", "！", "？", "!", "?", ".", "．", "…")

        /** 相邻词间隔超过该毫秒数视为一句结束。 */
        private const val SILENCE_GAP_MS = 1200L
    }
}
