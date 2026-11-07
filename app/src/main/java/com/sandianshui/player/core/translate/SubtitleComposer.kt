package com.sandianshui.player.core.translate

import com.sandianshui.player.core.translate.model.AsrSentence

/**
 * 把带时间戳的句子组装成标准 SRT 文本。
 * 纯函数，可单测。
 */
object SubtitleComposer {

    /**
     * 生成 SRT 文本。
     * @param sentences 已按开始时间升序排列的句子。
     * @param texts 与 sentences 等长的译文列表；为空时使用原文。
     */
    fun toSrt(sentences: List<AsrSentence>, texts: List<String>? = null): String {
        if (sentences.isEmpty()) return ""
        val sb = StringBuilder()
        for (i in sentences.indices) {
            val s = sentences[i]
            val text = texts?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: s.text
            if (i > 0) sb.append("\n")
            sb.append(i + 1).append("\n")
            sb.append(formatTime(s.beginMs)).append(" --> ").append(formatTime(s.endMs)).append("\n")
            sb.append(text).append("\n")
        }
        return sb.toString()
    }

    /** 毫秒 → SRT 时间戳 HH:MM:SS,mmm */
    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        val millis = ms % 1000
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }
}
