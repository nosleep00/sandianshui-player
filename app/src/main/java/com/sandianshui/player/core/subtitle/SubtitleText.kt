package com.sandianshui.player.core.subtitle

/**
 * 字幕文本清洗与时间戳解析的公共工具。
 */
internal object SubtitleText {

    /** 去除 UTF-8 BOM、统一换行符。 */
    fun clean(raw: String): String =
        raw.removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

    /** 去除 VTT 行内样式标签，如 <i>、</i>、<b>、<c.xxx> 等。 */
    fun stripTags(line: String): String =
        line.replace(Regex("<[^>]*>"), "").trim()

    /**
     * 解析通用时间戳，支持：
     *  - SRT：00:00:01,000
     *  - VTT：00:00:01.000 或 00:01.000（分:秒）
     *  - LRC：00:11.20（分:秒.百分秒）
     * 返回毫秒；无法解析返回 null。
     */
    fun parseTimeMs(token: String): Long? {
        val t = token.trim().replace(',', '.')
        val parts = t.split(':')
        val secWithFrac: String
        val hours: Long
        val minutes: Long
        when (parts.size) {
            3 -> {
                hours = parts[0].toLongOrNull() ?: return null
                minutes = parts[1].toLongOrNull() ?: return null
                secWithFrac = parts[2]
            }
            2 -> {
                hours = 0L
                minutes = parts[0].toLongOrNull() ?: return null
                secWithFrac = parts[1]
            }
            else -> return null
        }
        val secParts = secWithFrac.split('.')
        val seconds = secParts[0].toLongOrNull() ?: return null
        val fracMs = when (secParts.getOrNull(1)) {
            null -> 0L
            else -> {
                val f = secParts[1]
                // 2 位按百分秒、3 位按毫秒，其余补齐/截断到 3 位
                val normalized = when {
                    f.length == 2 -> f + "0"
                    f.length < 3 -> f.padEnd(3, '0')
                    else -> f.take(3)
                }
                normalized.toLongOrNull() ?: 0L
            }
        }
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + fracMs
    }

    /** 按空行把文本切成块。 */
    fun splitBlocks(text: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in text.split('\n')) {
            if (line.isBlank()) {
                if (current.isNotEmpty()) {
                    blocks.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(line)
            }
        }
        if (current.isNotEmpty()) blocks.add(current)
        return blocks
    }
}
