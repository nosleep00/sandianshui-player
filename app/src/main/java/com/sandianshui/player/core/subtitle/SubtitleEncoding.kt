package com.sandianshui.player.core.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 字幕 / 歌词文件可能使用的文本编码。
 *
 * @param displayName 界面上展示给用户的名称。
 * @param charsetName 实际解码用的 Charset 名称；[AUTO] 时为 null，由 [SubtitleDecoder] 自动识别。
 */
enum class SubtitleEncoding(val displayName: String, val charsetName: String?) {
    AUTO("自动", null),
    UTF8("UTF-8", "UTF-8"),
    UTF16LE("UTF-16LE", "UTF-16LE"),
    SHIFT_JIS("Shift-JIS", "Shift_JIS"),
    GBK("GBK", "GBK"),
    ;

    companion object {
        /** 用户手动可切换的具体编码（不含「自动」）。 */
        val manualOptions: List<SubtitleEncoding> =
            entries.filter { it != AUTO }
    }
}

/**
 * 字幕字节解码与编码自动识别。
 *
 * 自动识别顺序：
 *  1. BOM：UTF-16LE（FF FE）、UTF-8（EF BB BF）；
 *  2. 无 BOM 但明显是 UTF-16LE（ASCII 字符高字节为 0、交替出现）；
 *  3. 严格 UTF-8（出现非法字节即排除）；
 *  4. 双字节编码：Shift_JIS 与 GBK 用「结构合法性 + 日文假名」启发式判定。
 *
 * 该对象为纯逻辑（仅依赖 JDK Charset），可在本地单元测试中直接验证。
 */
object SubtitleDecoder {

    private val BOM_UTF16LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /**
     * 按指定编码解码字节；[SubtitleEncoding.AUTO] 时自动识别。
     * 解码后统一做 BOM / 换行清洗（[SubtitleText.clean]）。
     */
    fun decode(bytes: ByteArray, encoding: SubtitleEncoding): String {
        if (bytes.isEmpty()) return ""
        val resolved = if (encoding == SubtitleEncoding.AUTO) detect(bytes) else encoding
        val charsetName = resolved.charsetName ?: Charsets.UTF_8.name()
        val raw = runCatching {
            // 去掉 UTF-16LE 的 BOM，避免在句首产生占位字符
            val effective = if (resolved == SubtitleEncoding.UTF16LE && startsWith(bytes, BOM_UTF16LE)) {
                bytes.copyOfRange(2, bytes.size)
            } else {
                bytes
            }
            String(effective, Charset.forName(charsetName))
        }.getOrElse { String(bytes, Charsets.UTF_8) }
        return SubtitleText.clean(raw)
    }

    /** 自动识别字节编码。 */
    fun detect(bytes: ByteArray): SubtitleEncoding {
        if (bytes.isEmpty()) return SubtitleEncoding.UTF8
        if (startsWith(bytes, BOM_UTF16LE)) return SubtitleEncoding.UTF16LE
        if (startsWith(bytes, BOM_UTF8)) return SubtitleEncoding.UTF8
        if (looksLikeUtf16Le(bytes)) return SubtitleEncoding.UTF16LE
        if (isStrictlyDecodable(bytes, Charsets.UTF_8)) return SubtitleEncoding.UTF8
        return guessShiftJisOrGbk(bytes)
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) {
            if (bytes[i] != prefix[i]) return false
        }
        return true
    }

    /**
     * 无 BOM 的 UTF-16LE：ASCII 文本编码后每个字符占两字节、高字节为 0。
     * 抽样前若干个「字符位」，若绝大多数是高字节为 0 的可打印 ASCII，则判定为 UTF-16LE。
     */
    private fun looksLikeUtf16Le(bytes: ByteArray): Boolean {
        if (bytes.size < 16 || bytes.size % 2 != 0) return false
        var asciiChars = 0
        var sampled = 0
        var i = 0
        while (i + 1 < bytes.size && sampled < 64) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            if (hi == 0 && lo in 0x09..0x7E) asciiChars++
            sampled++
            i += 2
        }
        return sampled >= 8 && asciiChars.toDouble() / sampled > 0.7
    }

    /** 用严格模式（非法 / 不可映射字符即报错）判断字节能否按该字符集解码。 */
    private fun isStrictlyDecodable(bytes: ByteArray, charset: Charset): Boolean = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (e: CharacterCodingException) {
        false
    }

    /**
     * 区分 Shift_JIS 与 GBK（两者字节区间高度重叠）：
     *  - 解出日文假名（平假名 / 片假名）是 Shift_JIS 的强信号；
     *  - 否则按双字节结构合法性计数，违规少者优先；
     *  - 仍无法区分时按中文环境默认 GBK。
     */
    private fun guessShiftJisOrGbk(bytes: ByteArray): SubtitleEncoding {
        val sjisText = runCatching { String(bytes, Charset.forName("Shift_JIS")) }.getOrNull()
        val kanaCount = sjisText?.count { it in '぀'..'ヿ' } ?: 0
        if (kanaCount >= 2) return SubtitleEncoding.SHIFT_JIS

        val sjisViolations = countDbcViolations(
            bytes,
            isLead = { it in 0x81..0x9F || it in 0xE0..0xEF },
            isTrail = { it in 0x40..0x7E || it in 0x80..0xFC },
            isSingle = { it < 0x80 || it in 0xA1..0xDF }, // 半角片假名
        )
        val gbkViolations = countDbcViolations(
            bytes,
            isLead = { it in 0x81..0xFE },
            isTrail = { it in 0x40..0x7E || it in 0x80..0xFE },
            isSingle = { it < 0x80 },
        )
        return when {
            sjisViolations < gbkViolations -> SubtitleEncoding.SHIFT_JIS
            gbkViolations < sjisViolations -> SubtitleEncoding.GBK
            else -> SubtitleEncoding.GBK
        }
    }

    /**
     * 按双字节编码规则逐字节走一遍，统计结构违规次数。
     * 首字节（lead）后必须跟合法尾字节；单字节合法区间可独立出现。
     */
    private fun countDbcViolations(
        bytes: ByteArray,
        isLead: (Int) -> Boolean,
        isTrail: (Int) -> Boolean,
        isSingle: (Int) -> Boolean,
    ): Int {
        var violations = 0
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                isLead(b) -> {
                    val next = bytes.getOrNull(i + 1)?.toInt()?.and(0xFF)
                    if (next == null || !isTrail(next)) {
                        violations++
                        i++
                    } else {
                        i += 2
                    }
                }
                isSingle(b) -> i++
                else -> {
                    violations++
                    i++
                }
            }
        }
        return violations
    }
}
