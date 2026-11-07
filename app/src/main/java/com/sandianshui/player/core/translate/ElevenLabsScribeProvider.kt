package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import com.sandianshui.player.core.translate.model.AsrSentence
import org.json.JSONObject

/**
 * ElevenLabs Scribe 语音识别（scribe_v1）。
 *
 * 同步接口：把公网音频 URL 通过 multipart 表单的 source_url 字段提交，
 * 返回带词级时间戳的识别结果。本类再把词按「句末标点 / 停顿」聚合成句子。
 *
 * 文档：https://elevenlabs.io/docs/api-reference/speech-to-text/convert
 *
 * @param apiKey ElevenLabs API Key，通过 xi-api-key 请求头传递。
 */
class ElevenLabsScribeProvider(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
) : AsrProvider {

    override val name: String = "ElevenLabs Scribe"

    override suspend fun transcribe(
        audioUrl: String,
        language: String,
        onElapsed: (Int) -> Unit,
    ): List<AsrSentence> {
        onElapsed(1)
        val fields = linkedMapOf(
            "model_id" to model,
            "source_url" to audioUrl,
            "language_code" to language,
            "timestamps_granularity" to "word",
        )
        val (code, resp) = SimpleHttp.postMultipartFields(
            url = endpoint,
            fields = fields,
            headers = mapOf("xi-api-key" to apiKey),
            readTimeoutMs = 600_000,
        )
        if (code !in 200..299 || resp == null) {
            throw TranslateException("ElevenLabs 识别失败（HTTP $code）：${resp?.take(200)}")
        }
        onElapsed(2)
        return parseSentences(resp)
    }

    /**
     * 把 Scribe 返回的词级时间戳聚合为句子。
     * 纯函数，可单测。
     */
    fun parseSentences(json: String): List<AsrSentence> {
        val obj = JSONObject(json)
        val words = obj.optJSONArray("words") ?: return emptyList()
        data class Word(val start: Double, val end: Double, val text: String)

        val parsed = ArrayList<Word>(words.length())
        for (i in 0 until words.length()) {
            val w = words.optJSONObject(i) ?: continue
            val text = w.optString("text", "").trim()
            if (text.isEmpty()) continue
            parsed.add(
                Word(
                    start = w.optDouble("start", 0.0),
                    end = w.optDouble("end", w.optDouble("start", 0.0)),
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
                sentences.add(
                    AsrSentence(
                        beginMs = (segStart * 1000).toLong(),
                        endMs = (segEnd * 1000).toLong(),
                        text = t,
                    ),
                )
            }
            buffer.setLength(0)
        }

        for (i in parsed.indices) {
            val w = parsed[i]
            if (buffer.isEmpty()) segStart = w.start
            // 词与词之间保留原有空格（拉丁语言），日文无空格也无妨
            if (buffer.isNotEmpty() && needsSpaceBefore(buffer, w.text)) buffer.append(' ')
            buffer.append(w.text)
            segEnd = w.end

            val endsSentence = SENTENCE_ENDINGS.any { w.text.endsWith(it) }
            val next = parsed.getOrNull(i + 1)
            val longPause = next != null && (next.start - w.end) >= SILENCE_GAP_SEC
            if (endsSentence || longPause) {
                flush()
                if (next != null) segStart = next.start
            }
        }
        flush()
        return sentences
    }

    /** 简单判断词之间是否需要空格：当前词与下一词都像拉丁字符时加空格。 */
    private fun needsSpaceBefore(buffer: StringBuilder, next: String): Boolean {
        val last = buffer.lastOrNull() ?: return false
        val first = next.firstOrNull() ?: return false
        return last.isLatinLike() && first.isLatinLike()
    }

    private fun Char.isLatinLike(): Boolean =
        this.code in 0x41..0x7A || this == '\'' || this == '-'

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text"
        const val DEFAULT_MODEL = "scribe_v1"

        /** 句末标点（中文/日文/英文）。 */
        private val SENTENCE_ENDINGS = listOf("。", "！", "？", "!", "?", ".", "．", "…")

        /** 相邻词间隔超过该秒数视为一句结束。 */
        private const val SILENCE_GAP_SEC = 1.2
    }
}
