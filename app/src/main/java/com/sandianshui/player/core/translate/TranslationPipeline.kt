package com.sandianshui.player.core.translate

import com.sandianshui.player.core.translate.model.TranslationResult

/**
 * 翻译管线编排：ASR → 批量翻译 → SRT 组装。
 *
 * 通过 [onProgress] 回调驱动 UI 进度。结果可由 [TranslationCache] 缓存。
 */
class TranslationPipeline(
    private val asr: AsrProvider,
    private val translator: TranslatorProvider,
    private val cache: TranslationCache? = null,
) {
    /** 管线进度。 */
    sealed class Progress {
        data class AsrRunning(val elapsedSeconds: Int) : Progress()
        data class Translating(val sentenceCount: Int) : Progress()
        data class Done(val result: TranslationResult) : Progress()
        data class Failed(val reason: String) : Progress()
    }

    /**
     * 执行完整翻译管线。
     * @param audioUrl 公网可访问的音频 URL。
     * @param sourceLang 源语言（默认日语）。
     * @param targetLang 目标语言（默认简体中文）。
     * @param onProgress 进度回调，在调用线程（IO）触发，上层应切到主线程更新 UI。
     */
    suspend fun translate(
        audioUrl: String,
        sourceLang: String = "ja",
        targetLang: String = "zh",
        onProgress: (Progress) -> Unit,
    ) {
        // 1. 查缓存
        val cached = cache?.get(audioUrl)
        if (cached != null) {
            onProgress(Progress.Done(cached))
            return
        }

        try {
            // 2. ASR（onElapsed 回调驱动进度）
            val sentences = asr.transcribe(audioUrl, sourceLang) { elapsed ->
                onProgress(Progress.AsrRunning(elapsed))
            }
            if (sentences.isEmpty()) {
                onProgress(Progress.Failed("未识别到语音内容"))
                return
            }

            // 3. 翻译
            onProgress(Progress.Translating(sentences.size))
            val originals = sentences.map { it.text }
            val translated = translator.translate(originals, sourceLang, targetLang)

            // 4. 组装 SRT
            val srt = SubtitleComposer.toSrt(sentences, translated)
            val result = TranslationResult(srt, sentences)

            // 5. 写缓存
            cache?.put(audioUrl, result)

            onProgress(Progress.Done(result))
        } catch (e: TranslateException) {
            onProgress(Progress.Failed(e.message ?: "翻译失败"))
        } catch (e: Exception) {
            onProgress(Progress.Failed("翻译异常：${e.message}"))
        }
    }
}
