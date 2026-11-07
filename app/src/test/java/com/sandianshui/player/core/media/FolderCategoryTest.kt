package com.sandianshui.player.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderCategoryTest {

    @Test
    fun recognizesEnglishAudioFolders() {
        listOf("audio", "Audio", "music", "sounds", "songs").forEach {
            assertEquals(it, FolderCategory.AUDIO, FolderCategories.categoryOf(it))
        }
    }

    @Test
    fun recognizesEnglishSubtitleFolders() {
        listOf("subtitle", "subtitles", "sub", "captions", "srt").forEach {
            assertEquals(it, FolderCategory.SUBTITLE, FolderCategories.categoryOf(it))
        }
    }

    @Test
    fun recognizesEnglishLyricsAndImageFolders() {
        listOf("lyrics", "lyric", "lrc").forEach {
            assertEquals(it, FolderCategory.LYRICS, FolderCategories.categoryOf(it))
        }
        listOf("cover", "covers", "images", "pictures").forEach {
            assertEquals(it, FolderCategory.IMAGE, FolderCategories.categoryOf(it))
        }
    }

    @Test
    fun recognizesChineseFolders() {
        assertEquals(FolderCategory.AUDIO, FolderCategories.categoryOf("音频文件"))
        assertEquals(FolderCategory.AUDIO, FolderCategories.categoryOf("音乐"))
        assertEquals(FolderCategory.SUBTITLE, FolderCategories.categoryOf("中文字幕"))
        assertEquals(FolderCategory.SUBTITLE, FolderCategories.categoryOf("文本"))
        assertEquals(FolderCategory.SUBTITLE, FolderCategories.categoryOf("台本"))
        assertEquals(FolderCategory.LYRICS, FolderCategories.categoryOf("歌词"))
        assertEquals(FolderCategory.IMAGE, FolderCategories.categoryOf("封面图片"))
    }

    @Test
    fun tokenMatchingAvoidsFalsePositives() {
        // "sub" 只是 subject 的一部分，不应判为字幕
        assertEquals(FolderCategory.UNKNOWN, FolderCategories.categoryOf("subject"))
        // 无关键字的普通目录
        assertEquals(FolderCategory.UNKNOWN, FolderCategories.categoryOf("CD1"))
        assertEquals(FolderCategory.UNKNOWN, FolderCategories.categoryOf(""))
        assertEquals(FolderCategory.UNKNOWN, FolderCategories.categoryOf(null))
    }
}
