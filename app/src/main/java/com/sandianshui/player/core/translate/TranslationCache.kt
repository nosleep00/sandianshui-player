package com.sandianshui.player.core.translate

import android.content.Context
import com.sandianshui.player.core.translate.model.AsrSentence
import com.sandianshui.player.core.translate.model.TranslationResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 翻译结果文件缓存。
 *
 * key = SHA-256(audioUrl) 前 16 位；value = JSON（srtText + sentences）。
 * 存储在 cacheDir/translate/，LRU 上限 [maxEntries] 条，超量删最旧。
 * 作用：同一首歌重复翻译不重复扣费。
 */
class TranslationCache(
    context: Context,
    private val maxEntries: Int = 50,
) {
    private val dir = File(context.cacheDir, "translate").apply { mkdirs() }

    /** 读取缓存；未命中返回 null。 */
    fun get(audioUrl: String): TranslationResult? {
        val f = fileFor(audioUrl)
        if (!f.exists()) return null
        return try {
            val json = JSONObject(f.readText(Charsets.UTF_8))
            val srt = json.optString("srt", "")
            val arr = json.optJSONArray("sentences") ?: JSONArray()
            val sentences = ArrayList<AsrSentence>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                sentences.add(
                    AsrSentence(
                        beginMs = o.optLong("begin"),
                        endMs = o.optLong("end"),
                        text = o.optString("text"),
                    ),
                )
            }
            TranslationResult(srt, sentences)
        } catch (e: Exception) {
            null
        }
    }

    /** 写入缓存；超量时删除最旧的文件。 */
    fun put(audioUrl: String, result: TranslationResult) {
        val json = JSONObject().apply {
            put("srt", result.srtText)
            put(
                "sentences",
                JSONArray().apply {
                    result.originalSentences.forEach { s ->
                        put(JSONObject().apply {
                            put("begin", s.beginMs)
                            put("end", s.endMs)
                            put("text", s.text)
                        })
                    }
                },
            )
        }
        fileFor(audioUrl).writeText(json.toString(), Charsets.UTF_8)
        evictIfNeeded()
    }

    private fun fileFor(audioUrl: String): File {
        val key = sha256(audioUrl).take(16)
        return File(dir, "$key.json")
    }

    private fun evictIfNeeded() {
        val files = dir.listFiles { f -> f.extension == "json" }?.toList() ?: return
        if (files.size <= maxEntries) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxEntries)
            .forEach { it.delete() }
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
