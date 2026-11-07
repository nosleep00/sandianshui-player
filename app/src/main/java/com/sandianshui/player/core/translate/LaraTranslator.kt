package com.sandianshui.player.core.translate

import com.sandianshui.player.core.remote.SimpleHttp
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Lara Translate（Translated 公司）批量翻译。
 *
 * 认证（Access Key 流程）：
 * 1. 用 Access Key Secret 对规范串 `METHOD\nPATH\nCONTENT-MD5\nCONTENT-TYPE\nDATE`
 *    做 HMAC-SHA256 再 Base64，POST /v2/auth 换取 JWT（10 分钟有效）。
 * 2. 翻译请求带 `Authorization: Bearer <JWT>`。
 * 3. JWT 过期（401）时自动重新认证并重试一次，无需保存 refresh token。
 *
 * 翻译：POST /translate，请求体 `{"source","target","q":[...]}`，
 * 响应 content.translation[] 按顺序对齐。
 *
 * 文档：https://developers.laratranslate.com/docs/switch-from-modernmt
 *
 * @param accessKeyId Lara Access Key ID（如 FDJB8...）。
 * @param accessKeySecret Lara Access Key Secret（v_ 开头）。
 */
class LaraTranslator(
    private val accessKeyId: String,
    private val accessKeySecret: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
) : TranslatorProvider {

    override val name: String = "Lara Translate"

    /** 当前缓存的 JWT；为空时调用 [authenticate] 获取。 */
    @Volatile
    private var token: String? = null

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
            val translated = translateBatchWithRetry(texts, source, target)
            for (i in translated.indices) {
                if (i < indices.size) result[indices[i]] = translated[i]
            }
        }
        // 翻译失败的句子保留原文
        return result.mapIndexed { i, v -> v ?: sentences[i] }
    }

    /** 翻译一批，401 时重认证并重试一次。 */
    private suspend fun translateBatchWithRetry(
        texts: List<String>,
        source: String,
        target: String,
    ): List<String> {
        ensureToken()
        val (code, parsed) = requestTranslate(texts, source, target)
        if (code == 401) {
            token = null
            ensureToken()
            val (code2, parsed2) = requestTranslate(texts, source, target)
            if (code2 !in 200..299 || parsed2 == null) {
                throw TranslateException("Lara 翻译失败（HTTP $code2）")
            }
            return parsed2
        }
        if (code !in 200..299 || parsed == null) {
            throw TranslateException("Lara 翻译失败（HTTP $code）")
        }
        return parsed
    }

    private fun requestTranslate(
        texts: List<String>,
        source: String,
        target: String,
    ): Pair<Int, List<String>?> {
        val q = JSONArray()
        texts.forEach { q.put(it) }
        val body = JSONObject().apply {
            put("source", mapSource(source))
            put("target", mapTarget(target))
            put("q", q)
        }.toString()
        val (code, resp) = SimpleHttp.postJson(
            url = "$endpoint/translate",
            jsonBody = body,
            headers = mapOf("Authorization" to "Bearer ${token.orEmpty()}"),
            readTimeoutMs = 120_000,
        )
        if (code !in 200..299 || resp == null) return code to null
        return code to parseTranslations(resp)
    }

    /**
     * 解析 /translate 响应 content.translation[]。
     * 纯函数，可单测。响应结构不符时返回 null。
     */
    fun parseTranslations(json: String): List<String>? {
        val arr = JSONObject(json).optJSONObject("content")?.optJSONArray("translation")
            ?: return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) out.add(arr.optString(i))
        return out
    }

    private fun ensureToken() {
        if (token != null) return
        token = authenticate()
    }

    /**
     * Access Key 认证：构造签名并 POST /v2/auth，返回 JWT。
     */
    fun authenticate(): String {
        val path = "/v2/auth"
        val date = GMT_FMT.format(Date())
        // 紧凑 JSON（无空格），Content-MD5 必须与实际发送 body 一致
        val body = """{"id":"$accessKeyId"}"""
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val contentMd5 = Base64.getEncoder().encodeToString(md5(bodyBytes))
        val canonical = "POST\n$path\n$contentMd5\napplication/json\n$date"
        val signature = hmacSha256Base64(accessKeySecret, canonical)
        val (code, resp) = SimpleHttp.postJson(
            url = "$endpoint$path",
            jsonBody = body,
            headers = mapOf(
                "Authorization" to "Lara:$signature",
                "X-Lara-Date" to date,
                "Content-MD5" to contentMd5,
            ),
        )
        if (code !in 200..299 || resp == null) {
            throw TranslateException("Lara 认证失败（HTTP $code）：${resp?.take(200)}")
        }
        return JSONObject(resp).optString("token").also {
            if (it.isNullOrBlank()) throw TranslateException("Lara 认证响应缺少 token")
        }
    }

    private fun mapSource(code: String): String = when (code) {
        "zh" -> "zh-CN"
        "en" -> "en-US"
        "ja" -> "ja-JP"
        else -> code
    }

    private fun mapTarget(code: String): String = when (code) {
        "zh" -> "zh-CN"
        "en" -> "en-US"
        "ja" -> "ja-JP"
        else -> code
    }

    private fun md5(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(bytes)

    /** 构造认证签名用的规范串。internal 以便单测。 */
    internal fun buildCanonical(
        method: String,
        path: String,
        contentMd5: String,
        contentType: String,
        date: String,
    ): String = "$method\n$path\n$contentMd5\n$contentType\n$date"

    internal fun hmacSha256Base64(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)))
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.laratranslate.com"
        const val BATCH_SIZE = 50

        private val GMT_FMT: SimpleDateFormat =
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
    }
}
