package com.sandianshui.player.core.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleMatcherTest {

    @Test
    fun subtitleSrtWinsOverLyrics() {
        val files = listOf("静夜思.mp3", "静夜思.srt", "静夜思.lrc", "其他.txt")
        val match = SubtitleMatcher.match("静夜思", files)
        assertEquals(SubtitleFormat.SRT, match?.format)
        assertEquals("静夜思.srt", match?.fileName)
    }

    @Test
    fun vttWinsOverLrc() {
        val files = listOf("a.wav", "a.vtt", "a.lrc")
        assertEquals(SubtitleFormat.VTT, SubtitleMatcher.match("a", files)?.format)
    }

    @Test
    fun fallsBackToLrcWhenNoSubtitle() {
        val files = listOf("song.mp3", "song.lrc")
        assertEquals(SubtitleFormat.LRC, SubtitleMatcher.match("song", files)?.format)
    }

    @Test
    fun returnsNullWhenNothingMatches() {
        val files = listOf("song.mp3", "cover.png")
        assertNull(SubtitleMatcher.match("song", files))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val files = listOf("Song.MP3", "SONG.SRT")
        assertEquals(SubtitleFormat.SRT, SubtitleMatcher.match("song", files)?.format)
    }

    @Test
    fun chineseVariantPreferred_overOtherVariant() {
        // Asmr 真实命名：同一首音频同时有 .zh.srt 与 .16k.srt，应优先中文 .zh
        val base = "01_謁見の間～戦況報告"
        val files = listOf(
            "$base.mp3",
            "$base.16k.srt",
            "$base.zh.srt",
        )
        val match = SubtitleMatcher.match(base, files)
        assertEquals(SubtitleFormat.SRT, match?.format)
        assertEquals("$base.zh.srt", match?.fileName)
    }

    @Test
    fun exactBaseNameBeatsVariant() {
        // 精确同名 base.srt 优先于 base.zh.srt
        val files = listOf("a.mp3", "a.zh.srt", "a.srt")
        assertEquals("a.srt", SubtitleMatcher.match("a", files)?.fileName)
    }

    @Test
    fun variantWithoutDotSeparator_doesNotMatch() {
        // 仅前缀相同、但音频名后不是句点（song2），不能算 song 的字幕
        val files = listOf("song.mp3", "song2.zh.srt")
        assertNull(SubtitleMatcher.match("song", files))
    }

    @Test
    fun chineseVariantTagIsCaseInsensitive() {
        val files = listOf("a.mp3", "a.16k.srt", "a.ZH-CN.SRT")
        assertEquals("a.ZH-CN.SRT", SubtitleMatcher.match("a", files)?.fileName)
    }

    @Test
    fun variantFallsBackToLrc() {
        val files = listOf("a.mp3", "a.zh.lrc")
        assertEquals(SubtitleFormat.LRC, SubtitleMatcher.match("a", files)?.format)
    }

    @Test
    fun baseNameContainingDot_matchesVariant() {
        // 音频名本身含句点：my.audio.mp3 -> my.audio.zh.srt
        val files = listOf("my.audio.mp3", "my.audio.16k.srt", "my.audio.zh.srt")
        assertEquals("my.audio.zh.srt", SubtitleMatcher.match("my.audio", files)?.fileName)
    }

    @Test
    fun doesNotMatchAnotherTrackWithSamePrefix() {
        // 01 与 02 两首音频，01 不应匹配到 02 的字幕
        val files = listOf(
            "01_a.mp3", "02_a.mp3",
            "01_a.zh.srt", "02_a.zh.srt",
        )
        assertEquals("01_a.zh.srt", SubtitleMatcher.match("01_a", files)?.fileName)
        assertEquals("02_a.zh.srt", SubtitleMatcher.match("02_a", files)?.fileName)
    }

    @Test
    fun srtSavedAsTxt_stillMatches() {
        // 记事本 / 浏览器另存为 .txt：03.srt.txt 应识别为 03 的 SRT 字幕
        val base = "03.『今からシャーレに向かいますので』"
        val files = listOf("$base.mp3", "$base.srt.txt", "読んでね.txt")
        val match = SubtitleMatcher.match(base, files)
        assertEquals(SubtitleFormat.SRT, match?.format)
        assertEquals("$base.srt.txt", match?.fileName)
    }

    @Test
    fun lrcSavedAsTxt_stillMatches() {
        val files = listOf("song.mp3", "song.lrc.txt")
        assertEquals(SubtitleFormat.LRC, SubtitleMatcher.match("song", files)?.format)
    }

    @Test
    fun plainTextSidecar_isIgnored() {
        // 说明性 txt（不含字幕扩展名）仍应被忽略
        val files = listOf("song.mp3", "読んでね.txt", "notes.txt")
        assertNull(SubtitleMatcher.match("song", files))
    }

    // ===== 深度检索 matchDeep：严格优先，模糊包含兜底 =====

    @Test
    fun deepSearch_strictMatchWinsBeforeFuzzy() {
        // 同时存在严格同名和模糊包含，应选严格的
        val files = listOf("song.mp3", "song.zh.srt", "song_完整版.srt")
        val match = SubtitleMatcher.matchDeep("song", files)
        assertEquals(SubtitleFormat.SRT, match?.format)
        assertEquals("song.zh.srt", match?.fileName)
    }

    @Test
    fun deepSearch_fuzzyFallsBackWhenNoStrict() {
        // 没有严格同名时，字幕主干包含音频名也能匹配（跨目录常见命名）
        val files = listOf("01_謁見の間.mp3", "01_謁見の間～戦況報告.zh.srt")
        val match = SubtitleMatcher.matchDeep("01_謁見の間", files)
        assertEquals(SubtitleFormat.SRT, match?.format)
        assertEquals("01_謁見の間～戦況報告.zh.srt", match?.fileName)
    }

    @Test
    fun deepSearch_fuzzyAudioContainsSubtitleStem() {
        // 反向：音频名包含字幕主干
        val files = listOf("歌曲_完整版.mp3", "歌曲.srt")
        val match = SubtitleMatcher.matchDeep("歌曲_完整版", files)
        assertEquals("歌曲.srt", match?.fileName)
    }

    @Test
    fun deepSearch_subtitleContainmentPreferredOverReverse() {
        // 字幕主干包含音频名（rank 0）优先于音频名包含字幕主干（rank 1）
        val files = listOf("ab.mp3", "ab_full.srt", "a.srt")
        // "ab_full" contains "ab" -> rank 0; "a" is contained in "ab" -> rank 1
        val match = SubtitleMatcher.matchDeep("ab", files)
        assertEquals("ab_full.srt", match?.fileName)
    }

    @Test
    fun deepSearch_tooShortStem_doesNotFuzzyMatch() {
        // 过短名字（<3 字符）不参与模糊匹配，避免误匹配
        val files = listOf("a.mp3", "abc.srt")
        assertNull(SubtitleMatcher.matchDeep("a", files))
    }

    @Test
    fun deepSearch_returnsNullWhenNothingRelevant() {
        val files = listOf("song.mp3", "unrelated_video.srt", "cover.png")
        assertNull(SubtitleMatcher.matchDeep("song", files))
    }

    @Test
    fun deepSearch_srtStillWinsOverLrcInFuzzy() {
        val files = listOf("song.mp3", "song_full.lrc", "song_full.srt")
        assertEquals(SubtitleFormat.SRT, SubtitleMatcher.matchDeep("song", files)?.format)
    }

    @Test
    fun matchCandidates_returnsStrictOnlyWhenPresent() {
        val files = listOf("song.mp3", "song.srt", "song_other.srt")
        val candidates = SubtitleMatcher.matchCandidates("song", files)
        assertEquals(1, candidates.size)
        assertEquals("song.srt", candidates[0].fileName)
    }

    @Test
    fun matchCandidates_fallbackToFuzzyList() {
        val files = listOf("song.mp3", "song_full.srt", "song_full.vtt")
        val candidates = SubtitleMatcher.matchCandidates("song", files)
        // 无严格匹配时，模糊候选按 SRT -> VTT 排序
        assertEquals(2, candidates.size)
        assertEquals(SubtitleFormat.SRT, candidates[0].format)
        assertEquals(SubtitleFormat.VTT, candidates[1].format)
    }
}
