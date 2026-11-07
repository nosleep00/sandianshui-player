package com.sandianshui.player.core.remote

import com.sandianshui.player.core.subtitle.SubtitleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsmrOneTest {

    private val fixture = """
        {
          "works": [
            {
              "id": 403038,
              "title": "【ブルーアーカイブ】ユウカASMR～頑張るあなたのすぐそばに～",
              "name": "Yostar",
              "circle": {"id": 1, "name": "Yostar"},
              "vas": [{"id": 9, "name": "春花らん"}],
              "tags": [{"id": 1, "name": "治愈"}],
              "duration": 7808,
              "dl_count": 90076,
              "has_subtitle": false,
              "mainCoverUrl": "https://example.com/api/cover/403038.jpg?type=main"
            },
            {
              "id": 1078257,
              "title": "第二部",
              "circle": {"name": "Yostar"},
              "vas": [],
              "duration": null,
              "dl_count": 77984,
              "mainCoverUrl": ""
            }
          ],
          "pagination": {"currentPage": 1, "pageSize": 10, "totalCount": 19}
        }
    """.trimIndent()

    @Test
    fun parse_returnsAllWorks() {
        val works = AsmrOne.parse(fixture)
        assertEquals(2, works.size)
    }

    @Test
    fun parse_firstWork_mapsFields() {
        val w = AsmrOne.parse(fixture)[0]
        assertEquals(403038, w.id)
        assertEquals("【ブルーアーカイブ】ユウカASMR～頑張るあなたのすぐそばに～", w.title)
        assertEquals("Yostar", w.circle)
        assertEquals(listOf("春花らん"), w.vas)
        assertEquals(7808, w.durationSec)
        assertEquals(90076, w.dlCount)
        assertEquals("https://example.com/api/cover/403038.jpg?type=main", w.coverUrl)
    }

    @Test
    fun parse_nullDurationAndEmptyCover_areHandled() {
        val w = AsmrOne.parse(fixture)[1]
        assertNull(w.durationSec)
        assertNull(w.coverUrl)
    }

    @Test
    fun parse_emptyWorksArray_returnsEmpty() {
        val works = AsmrOne.parse("""{"works":[],"pagination":{}}""")
        assertTrue(works.isEmpty())
    }

    @Test
    fun parse_missingWorksKey_returnsEmpty() {
        val works = AsmrOne.parse("""{"pagination":{}}""")
        assertTrue(works.isEmpty())
    }

    // ===== parseSearchResult：分页信息 =====

    @Test
    fun parseSearchResult_mapsPaginationFields() {
        val r = AsmrOne.parseSearchResult(fixture)
        assertEquals(1, r.currentPage)
        assertEquals(10, r.pageSize)
        assertEquals(19, r.totalCount)
        assertEquals(2, r.works.size)
    }

    @Test
    fun parseSearchResult_computesTotalPagesWhenMissing() {
        // totalCount=19, pageSize=10，未给 totalPages → ceil(19/10)=2
        val r = AsmrOne.parseSearchResult(fixture)
        assertEquals(2, r.totalPages)
    }

    @Test
    fun parseSearchResult_usesServerTotalPagesWhenPresent() {
        val json = """
            {"works":[{"id":1,"title":"t","vas":[]}],
             "pagination":{"currentPage":3,"pageSize":20,"totalCount":120,"totalPages":6}}
        """.trimIndent()
        val r = AsmrOne.parseSearchResult(json)
        assertEquals(3, r.currentPage)
        assertEquals(20, r.pageSize)
        assertEquals(120, r.totalCount)
        assertEquals(6, r.totalPages)
    }

    @Test
    fun parseSearchResult_pageSize20YieldsOnePageFor20Items() {
        val works = (1..20).joinToString(",") {
            """{"id":$it,"title":"t$it","vas":[]}"""
        }
        val json = """
            {"works":[$works],
             "pagination":{"currentPage":1,"pageSize":20,"totalCount":20}}
        """.trimIndent()
        val r = AsmrOne.parseSearchResult(json)
        assertEquals(20, r.works.size)
        assertEquals(1, r.totalPages)
    }

    @Test
    fun parseSearchResult_emptyResultsHaveZeroPages() {
        val r = AsmrOne.parseSearchResult("""{"works":[],"pagination":{"totalCount":0}}""")
        assertEquals(0, r.totalPages)
        assertTrue(r.works.isEmpty())
    }

    @Test
    fun parse_blankTitleFallsBackToPlaceholder() {
        val works = AsmrOne.parse(
            """{"works":[{"id":1,"title":"","circle":{"name":"C"},"vas":[]}]}""",
        )
        assertEquals("未命名", works[0].title)
    }

    @Test
    fun parse_multipleVas_areAllCollected() {
        val works = AsmrOne.parse(
            """{"works":[{"id":1,"title":"t","vas":[{"name":"A"},{"name":"B"}]}]}""",
        )
        assertEquals(listOf("A", "B"), works[0].vas)
    }

    @Test
    fun parse_blankVaNames_areSkipped() {
        val works = AsmrOne.parse(
            """{"works":[{"id":1,"title":"t","vas":[{"name":""},{"name":"C"}]}]}""",
        )
        assertEquals(listOf("C"), works[0].vas)
        assertFalse(works[0].vas.contains(""))
    }

    // ===== parseTracks =====

    private val tracksFixture = """
        [
          {
            "type": "folder", "title": "mp3",
            "children": [
              {
                "type": "folder", "title": "ボーナストラック",
                "children": [
                  {"type": "audio", "title": "06. bonus.mp3", "duration": 900.0,
                   "mediaStreamUrl": "https://h/06.mp3", "mediaDownloadUrl": "https://d/06.mp3"}
                ]
              },
              {"type": "audio", "title": "01. first.mp3", "duration": 9.7,
               "mediaStreamUrl": "https://h/01.mp3", "mediaDownloadUrl": "https://d/01.mp3"},
              {"type": "audio", "title": "02. second.mp3", "duration": null,
               "mediaStreamUrl": "https://h/02.mp3", "mediaDownloadUrl": "https://d/02.mp3"}
            ]
          },
          {
            "type": "folder", "title": "wav",
            "children": [
              {"type": "audio", "title": "01. first.wav",
               "mediaStreamUrl": "https://h/wav01.wav", "mediaDownloadUrl": "https://d/wav01.wav"}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun parseTracks_collectsAllAudioAcrossFoldersAndSorts() {
        val t = AsmrOne.parseTracks(tracksFixture)
        assertEquals(4, t.size)
        assertEquals(
            listOf("01. first.mp3", "01. first.wav", "02. second.mp3", "06. bonus.mp3"),
            t.map { it.title },
        )
    }

    @Test
    fun parseTracks_collectsBothMp3AndWav() {
        val t = AsmrOne.parseTracks(tracksFixture)
        assertTrue(t.any { it.title.endsWith(".mp3") })
        assertTrue(t.any { it.title.endsWith(".wav") })
    }

    @Test
    fun parseTracks_mapsStreamUrlAndDuration() {
        val t = AsmrOne.parseTracks(tracksFixture)
        val first = t.first { it.title == "01. first.mp3" }
        assertEquals("https://h/01.mp3", first.streamUrl)
        assertEquals(9.7, first.durationSec!!, 0.0001)
        // 02. second.mp3 的 duration 为 null
        assertNull(t.first { it.title == "02. second.mp3" }.durationSec)
    }

    @Test
    fun parseTracks_collectsAudioInSingleFolder() {
        val json = """
            [{"type":"folder","title":"wav","children":[
              {"type":"audio","title":"01.wav","mediaStreamUrl":"https://h/01.wav"}
            ]}]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json)
        assertEquals(1, t.size)
        assertEquals("https://h/01.wav", t[0].streamUrl)
    }

    @Test
    fun parseTracks_usesDownloadUrlWhenStreamBlank() {
        val json = """
            [{"type":"folder","title":"mp3","children":[
              {"type":"audio","title":"01.mp3","mediaStreamUrl":"","mediaDownloadUrl":"https://d/01.mp3"}
            ]}]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json)
        assertEquals("https://d/01.mp3", t[0].streamUrl)
    }

    @Test
    fun parseTracks_skipsAudioWithoutAnyUrl() {
        val json = """
            [{"type":"folder","title":"mp3","children":[
              {"type":"audio","title":"01.mp3","mediaStreamUrl":"","mediaDownloadUrl":""}
            ]}]
        """.trimIndent()
        assertTrue(AsmrOne.parseTracks(json).isEmpty())
    }

    @Test
    fun parseTracks_emptyArray_returnsEmpty() {
        assertTrue(AsmrOne.parseTracks("[]").isEmpty())
    }

    // ===== parseTracks：在线字幕 / 歌词匹配 =====

    @Test
    fun parseTracks_matchesSameNameLrcInSameFolder() {
        val json = """
            [{"type":"folder","title":"01声あり","children":[
              {"type":"audio","title":"01.song.mp3","mediaStreamUrl":"https://h/01.mp3"},
              {"type":"text","title":"01.song.lrc","mediaStreamUrl":"https://api/lrc/1"}
            ]}]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json).first()
        assertEquals("https://api/lrc/1", t.subtitleUrl)
        assertEquals(SubtitleFormat.LRC, t.subtitleFormat)
    }

    @Test
    fun parseTracks_prefersSrtOverLrc() {
        val json = """
            [{"type":"folder","children":[
              {"type":"audio","title":"song.mp3","mediaStreamUrl":"https://h/a.mp3"},
              {"type":"text","title":"song.lrc","mediaStreamUrl":"https://api/lrc"},
              {"type":"text","title":"song.srt","mediaStreamUrl":"https://api/srt"}
            ]}]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json).first()
        assertEquals("https://api/srt", t.subtitleUrl)
        assertEquals(SubtitleFormat.SRT, t.subtitleFormat)
    }

    @Test
    fun parseTracks_ignoresPlainTxt() {
        val json = """
            [{"type":"folder","children":[
              {"type":"audio","title":"song.mp3","mediaStreamUrl":"https://h/a.mp3"},
              {"type":"text","title":"読んでね.txt","mediaStreamUrl":"https://api/txt"}
            ]}]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json).first()
        assertNull(t.subtitleUrl)
        assertNull(t.subtitleFormat)
    }

    @Test
    fun parseTracks_matchesSubtitleAtRootLevel() {
        val json = """
            [
              {"type":"audio","title":"01.intro.mp3","mediaStreamUrl":"https://h/intro.mp3"},
              {"type":"text","title":"01.intro.lrc","mediaStreamUrl":"https://api/intro-lrc"}
            ]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json).first()
        assertEquals("https://api/intro-lrc", t.subtitleUrl)
        assertEquals(SubtitleFormat.LRC, t.subtitleFormat)
    }

    @Test
    fun parseTracks_doesNotMatchSubtitleFromOtherFolder() {
        val json = """
            [
              {"type":"folder","title":"A","children":[
                {"type":"audio","title":"song.mp3","mediaStreamUrl":"https://h/a.mp3"}
              ]},
              {"type":"folder","title":"B","children":[
                {"type":"text","title":"song.lrc","mediaStreamUrl":"https://api/lrc"}
              ]}
            ]
        """.trimIndent()
        val t = AsmrOne.parseTracks(json).first { it.title == "song.mp3" }
        assertNull(t.subtitleUrl)
    }

    // ===== parseSubtitleCandidates：深度检索用，跨文件夹收集全部字幕 =====

    @Test
    fun parseSubtitleCandidates_collectsAcrossNestedFolders() {
        val json = """
            [
              {"type":"folder","title":"mp3","children":[
                {"type":"audio","title":"01.mp3","mediaStreamUrl":"https://h/01.mp3"}
              ]},
              {"type":"folder","title":"文本","children":[
                {"type":"folder","title":"TC","children":[
                  {"type":"text","title":"01.zh.srt","mediaStreamUrl":"https://api/01-zh"},
                  {"type":"text","title":"01.16k.srt","mediaStreamUrl":"https://api/01-16k"}
                ]}
              ]},
              {"type":"text","title":"読んでね.txt","mediaStreamUrl":"https://api/note"}
            ]
        """.trimIndent()
        val c = AsmrOne.parseSubtitleCandidates(json)
        assertEquals(2, c.size)
        assertTrue(c.any { it.fileName == "01.zh.srt" && it.format == SubtitleFormat.SRT })
        assertTrue(c.any { it.fileName == "01.16k.srt" && it.format == SubtitleFormat.SRT })
    }

    @Test
    fun parseSubtitleCandidates_ignoresPlainTxtAndNonText() {
        val json = """
            [
              {"type":"audio","title":"a.mp3","mediaStreamUrl":"https://h/a.mp3"},
              {"type":"text","title":"notes.txt","mediaStreamUrl":"https://api/n"},
              {"type":"text","title":"a.vtt","mediaStreamUrl":"https://api/v"}
            ]
        """.trimIndent()
        val c = AsmrOne.parseSubtitleCandidates(json)
        assertEquals(1, c.size)
        assertEquals("a.vtt", c[0].fileName)
        assertEquals(SubtitleFormat.VTT, c[0].format)
    }

    @Test
    fun parseSubtitleCandidates_usesDownloadUrlWhenStreamBlank() {
        val json = """
            [{"type":"text","title":"a.lrc","mediaStreamUrl":"","mediaDownloadUrl":"https://d/lrc"}]
        """.trimIndent()
        val c = AsmrOne.parseSubtitleCandidates(json)
        assertEquals(1, c.size)
        assertEquals("https://d/lrc", c[0].url)
    }

    @Test
    fun parseSubtitleCandidates_emptyArray_returnsEmpty() {
        assertTrue(AsmrOne.parseSubtitleCandidates("[]").isEmpty())
    }
}
