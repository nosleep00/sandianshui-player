package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import com.sandianshui.player.core.translate.model.AsrSentence
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 阿里百炼 paraformer-v2 非实时语音识别。
 *
 * 流程：提交任务（异步）→ 轮询任务状态 → 下载结果 JSON → 解析 sentences。
 * 仅接受公网可访问的音频 URL（不支持本地文件/Base64）。
 *
 * 文档：https://help.aliyun.com/zh/model-studio/paraformer-recorded-speech-recognition-restful-api
 */
class BailianAsrProvider(
    private val apiKey: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : AsrProvider {

    override val name: String = "阿里百炼 paraformer-v2"

    override suspend fun transcribe(
        audioUrl: String,
        language: String,
        onElapsed: (Int) -> Unit,
    ): List<AsrSentence> {
        val safeUrl = encodeUrlIfNeeded(audioUrl)
        val taskId = submitTask(safeUrl, language)
        val resultUrl = pollTask(taskId, onElapsed)
        val json = downloadResult(resultUrl)
        return parseSentences(json)
    }

    /** 提交识别任务，返回 task_id。 */
    private fun submitTask(audioUrl: String, language: String): String {
        val body = JSONObject().apply {
            put("model", "paraformer-v2")
            put("input", JSONObject().put("file_urls", JSONArray().put(audioUrl)))
            put(
                "parameters",
                JSONObject().apply {
                    put("channel_id", JSONArray().put(0))
                    put("language_hints", JSONArray().put(language))
                    put("timestamp_alignment_enabled", true)
                    put("disfluency_removal_enabled", false)
                },
            )
        }.toString()

        val (code, resp) = SimpleHttp.postJson(
            url = "$endpoint/api/v1/services/audio/asr/transcription",
            jsonBody = body,
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "X-DashScope-Async" to "enable",
            ),
        )
        if (code !in 200..299 || resp == null) {
            throw TranslateException("百炼 ASR 提交失败（HTTP $code）：${resp?.take(200)}")
        }
        val taskId = JSONObject(resp).optJSONObject("output")?.optString("task_id")
        if (taskId.isNullOrBlank()) {
            throw TranslateException("百炼 ASR 响应缺少 task_id：${resp.take(200)}")
        }
        return taskId
    }

    /** 轮询任务直到 SUCCEEDED / FAILED，返回结果下载 URL。 */
    private suspend fun pollTask(taskId: String, onElapsed: (Int) -> Unit): String {
        val start = System.currentTimeMillis()
        val maxWaitMs = 10 * 60 * 1000L // 10 分钟超时
        while (true) {
            val elapsed = ((System.currentTimeMillis() - start) / 1000).toInt()
            onElapsed(elapsed)
            if (elapsed * 1000L > maxWaitMs) {
                throw TranslateException("百炼 ASR 排队超过 10 分钟，请稍后重试")
            }
            val (code, resp) = SimpleHttp.postJson(
                url = "$endpoint/api/v1/tasks/$taskId",
                jsonBody = "{}",
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                readTimeoutMs = 30_000,
            )
            if (code !in 200..299 || resp == null) {
                delay(3000)
                continue
            }
            val output = JSONObject(resp).optJSONObject("output") ?: continue
            when (output.optString("task_status")) {
                "SUCCEEDED" -> {
                    val results = output.optJSONArray("results")
                    val first = results?.optJSONObject(0)
                    val subStatus = first?.optString("subtask_status")
                    if (subStatus == "FAILED") {
                        val msg = first.optString("message")
                        val code2 = first.optString("code")
                        throw TranslateException("百炼 ASR 识别失败（$code2）：$msg")
                    }
                    val url = first?.optString("transcription_url")
                    if (url.isNullOrBlank()) {
                        throw TranslateException("百炼 ASR 结果缺少 transcription_url")
                    }
                    return url
                }
                "FAILED" -> throw TranslateException("百炼 ASR 任务失败：${resp.take(200)}")
                else -> delay(3000) // PENDING / RUNNING
            }
        }
    }

    /** 下载识别结果 JSON（transcription_url，24h 有效）。 */
    private fun downloadResult(url: String): String {
        val (code, body) = SimpleHttp.get(url, UA, readTimeoutMs = 30_000)
        if (code !in 200..299 || body.isBlank()) {
            throw TranslateException("下载百炼 ASR 结果失败（HTTP $code）")
        }
        return body
    }

    /**
     * 解析结果 JSON 中的 sentences[]，过滤空句和过短句（<200ms）。
     * 纯函数，可单测。
     */
    fun parseSentences(json: String): List<AsrSentence> {
        val root = JSONObject(json)
        val transcripts = root.optJSONArray("transcripts") ?: return emptyList()
        val out = ArrayList<AsrSentence>()
        for (i in 0 until transcripts.length()) {
            val tr = transcripts.optJSONObject(i) ?: continue
            val sentences = tr.optJSONArray("sentences") ?: continue
            for (j in 0 until sentences.length()) {
                val s = sentences.optJSONObject(j) ?: continue
                val begin = s.optLong("begin_time", -1)
                val end = s.optLong("end_time", -1)
                val text = s.optString("text", "").trim()
                if (begin < 0 || end <= begin || text.isEmpty()) continue
                if (end - begin < 200) continue // 过滤过短噪音句
                out.add(AsrSentence(begin, end, text))
            }
        }
        return out
    }

    /** URL 路径中含中文/空格时进行编码（已编码的不重复编码）。 */
    private fun encodeUrlIfNeeded(url: String): String {
        if (url.all { it.code < 128 }) return url
        val q = url.indexOf('?')
        val path = if (q >= 0) url.substring(0, q) else url
        val query = if (q >= 0) url.substring(q) else ""
        val encoded = path.split('/').joinToString("/") { segment ->
            if (segment.all { it.code < 128 }) segment
            else URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return encoded + query
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com"
        private const val UA = "SandianShuiPlayer/1.0"
    }
}

/** 翻译管线中的可恢复异常。 */
class TranslateException(message: String) : Exception(message)
