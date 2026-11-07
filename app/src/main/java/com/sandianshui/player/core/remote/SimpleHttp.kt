package com.sandianshui.player.core.remote

import java.net.HttpURLConnection
import java.net.URL

/**
 * 极简 HTTP GET（基于 JDK 自带 HttpURLConnection，不引入额外依赖）。
 * openConnection() 会使用系统默认 ProxySelector：若设备在系统设置里配置了代理
 * （或模拟器启动时带 -http-proxy），请求会自动走代理，无需在代码里写死。
 */
object SimpleHttp {

    /**
     * 发起 GET。
     * @return (HTTP 状态码, 响应文本)；网络异常时返回 (0, "")。
     */
    fun get(
        url: String,
        userAgent: String,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 20_000,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, String> {
        val (code, bytes) = getBytes(url, userAgent, connectTimeoutMs, readTimeoutMs, headers)
        return code to (bytes?.toString(Charsets.UTF_8).orEmpty())
    }

    /**
     * 发起 GET 并返回原始字节，供字幕 / 歌词按多种编码（Shift_JIS / GBK / UTF-16LE）解码。
     * @return (HTTP 状态码, 响应字节)；网络异常时返回 (0, null)。
     */
    fun getBytes(
        url: String,
        userAgent: String,
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 20_000,
        headers: Map<String, String> = emptyMap(),
    ): Pair<Int, ByteArray?> {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes() }
            return code to body
        } catch (e: Exception) {
            android.util.Log.w("SimpleHttp", "GET failed: $url", e)
            return 0 to null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 发起 POST（JSON body）。
     * @param headers 额外请求头（如 Authorization）；Content-Type 已固定为 application/json。
     * @return (HTTP 状态码, 响应文本)；网络异常时返回 (0, null)。
     */
    fun postJson(
        url: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 120_000,
    ): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                doOutput = true
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8)
            return code to body
        } catch (e: Exception) {
            android.util.Log.w("SimpleHttp", "POST failed: $url", e)
            return 0 to null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 发起 POST（multipart/form-data，仅文本字段，不含文件）。
     * 适用于 ElevenLabs Scribe 等需要表单字段（如 source_url）的接口。
     * @param fields 表单字段键值对。
     * @param headers 额外请求头（如鉴权）；Content-Type 会自动带上 boundary。
     * @return (HTTP 状态码, 响应文本)；网络异常时返回 (0, null)。
     */
    fun postMultipartFields(
        url: String,
        fields: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 15_000,
        readTimeoutMs: Int = 300_000,
    ): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        try {
            val boundary = "----SdPlayerBoundary${System.currentTimeMillis()}"
            val crlf = "\r\n"
            val payload = buildString {
                fields.forEach { (name, value) ->
                    append("--").append(boundary).append(crlf)
                    append("Content-Disposition: form-data; name=\"").append(name).append("\"")
                    append(crlf).append(crlf)
                    append(value).append(crlf)
                }
                append("--").append(boundary).append("--").append(crlf)
            }
            val bytes = payload.toByteArray(Charsets.UTF_8)
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                doOutput = true
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes() }?.toString(Charsets.UTF_8)
            return code to body
        } catch (e: Exception) {
            android.util.Log.w("SimpleHttp", "POST multipart failed: $url", e)
            return 0 to null
        } finally {
            conn?.disconnect()
        }
    }
}
