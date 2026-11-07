package com.sandianshui.player.core.translate

import kotlin.math.ceil

/**
 * Groq gpt-oss-20b 的"上下文能力"档位（共 5 档）。
 *
 * 档位越高，翻译时参考的上下文越多、模型推理越深，译文更自然连贯，但耗时也越长。
 * 每一档由两个真正生效的参数控制：
 * 1. [reasoningEffort]：Groq 接口的 reasoning_effort（low / medium / high），
 *    控制模型思考深度；
 * 2. [contextSentences]：翻译每批句子时额外附带的前后文句数（仅作参考，不翻译），
 *    让模型理解对话语境，避免逐字硬译。
 *
 * 预估时间按批次计算：每批 [BATCH_SIZE] 句，[baseSecondsPerBatch] 为该档每批的
 * 经验耗时，[estimateSeconds] 给出总秒数。
 */
enum class GroqContextLevel(
    val level: Int,
    val label: String,
    val reasoningEffort: String,
    val contextSentences: Int,
    val baseSecondsPerBatch: Int,
) {
    /** 弱：最少推理、不附上下文，最快。 */
    WEAK(1, "弱", "low", 0, 12),

    /** 较弱：低推理、附 1 句上下文。 */
    LOWER(2, "较弱", "low", 1, 15),

    /** 中：中等推理、附 2 句上下文。 */
    MEDIUM(3, "中", "medium", 2, 20),

    /** 较强：中等推理、附 3 句上下文。 */
    HIGHER(4, "较强", "medium", 3, 26),

    /** 强：深度推理、附 4 句上下文，最自然也最慢。 */
    STRONG(5, "强", "high", 4, 35),
    ;

    /** 相对基准（弱档）的耗时倍数，用于配置界面展示。 */
    val timeMultiplier: Double
        get() = baseSecondsPerBatch.toDouble() / WEAK.baseSecondsPerBatch

    /**
     * 按句子总数预估翻译耗时（秒，不含语音识别时间）。
     * 纯函数，可单测。
     */
    fun estimateSeconds(sentenceCount: Int): Int {
        if (sentenceCount <= 0) return 0
        val batches = ceil(sentenceCount.toDouble() / GroqTranslator.BATCH_SIZE).toInt()
        return batches * baseSecondsPerBatch
    }

    /** 推理深度的中文说明。 */
    val reasoningLabel: String
        get() = when (reasoningEffort) {
            "low" -> "浅度推理"
            "medium" -> "中等推理"
            "high" -> "深度推理"
            else -> reasoningEffort
        }

    companion object {
        /** 默认档位（保持与旧版 low 行为接近，略有提升）。 */
        val DEFAULT = MEDIUM

        /** 按 1..5 的档位序号解析，越界回退默认。 */
        fun fromLevel(level: Int): GroqContextLevel =
            entries.firstOrNull { it.level == level } ?: DEFAULT

        /** 按 0..4 的下标（滑杆位置）解析。 */
        fun fromOrdinal(ordinal: Int): GroqContextLevel =
            entries.getOrNull(ordinal) ?: DEFAULT
    }
}
