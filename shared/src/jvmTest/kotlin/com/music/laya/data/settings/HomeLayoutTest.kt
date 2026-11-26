package com.music.laya.data.settings

import com.music.laya.data.model.HomeShelf
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeLayoutTest {
    private val playlist = HomePlaylist("VLPLfav", "Favorites", null, "Playlist")
    private val feed = listOf("Quick picks", "Recents", "나를 위한 추천", "Artists on the rise")
        .map { HomeShelf(it, emptyList()) }

    @Test
    fun putsMyPlaylistFirstBelowRecentsByDefaultAndHidesItWhenEmpty() {
        val id = HomeSection.MY_PLAYLIST.id
        val layout = HomeLayout(order = listOf("QUICK_PICKS"))
        assertEquals(id, layout.editorOrder(feed).first())
        assertFalse(layout.isVisible(id, "account:main"))
        assertEquals(listOf("Recents", "Quick picks", "나를 위한 추천", "Artists on the rise"),
            layout.applyTo(feed, "account:main").map { it.title })
        val filled = layout.copy(playlistsByProfile = mapOf("account:main" to listOf(playlist)))
        assertTrue(filled.isVisible(id, "account:main"))
        assertEquals(listOf("Recents", "My Playlist", "Quick picks", "나를 위한 추천", "Artists on the rise"),
            filled.applyTo(feed, "account:main").map { it.title })
        assertEquals("My Playlist", filled.applyTo(feed.filterNot { it.title == "Recents" }, "account:main").first().title)
    }

    @Test
    fun keepsRecentsFixedAndMovesTheCustomShelfWithRecommendations() {
        val layout = HomeLayout(
            order = listOf("FOR_YOU", "MY_PLAYLIST", "QUICK_PICKS"),
            hidden = setOf("QUICK_PICKS"),
            playlistsByProfile = mapOf("account:main" to listOf(playlist)),
        )
        val visible = layout.applyTo(feed, "account:main")
        assertEquals(listOf("Recents", "나를 위한 추천", "My Playlist", "Artists on the rise"), visible.map { it.title })
        assertEquals("VLPLfav", visible[2].items.single().browseId)
        assertFalse("RECENTS" in layout.editorOrder(feed))
        assertEquals(listOf("Recents", "나를 위한 추천", "Artists on the rise"),
            layout.applyTo(feed, "account:other").map { it.title })
        assertEquals(listOf("Recents", "나를 위한 추천", "Artists on the rise"),
            layout.copy(hidden = layout.hidden + "MY_PLAYLIST").applyTo(feed, "account:main").map { it.title })
    }

    @Test
    fun usesTheSameKeysAcrossEnglishAndKorean() {
        val pairs = listOf(
            "RecentLY played" to "최근 재생",
            "Albums & singles" to "앨범 및 싱글",
            "Trending" to "인기",
            "Trending songs for you" to "나만을 위한 인기 급상승 곡",
            "Today's discoveries" to "오늘의 발견",
            "Mixed for you" to "맞춤 믹스",
        )
        for ((english, korean) in pairs) {
            val englishKey = HomeSection.idFor(HomeShelf(english, emptyList()))
            val koreanKey = HomeSection.idFor(HomeShelf(korean, emptyList()))
            assertFalse(englishKey.startsWith("feed:"))
            assertEquals(englishKey, koreanKey)
        }
    }

    @Test
    fun restoresLayoutAndResetsSectionEditsWithoutDeletingPlaylists() {
        val settings = MapSettings()
        try {
            HomeLayoutPreferences.install(settings)
            HomeLayoutPreferences.reload()
            HomeLayoutPreferences.setPlaylists("account:main", listOf(playlist))
            HomeLayoutPreferences.setOrder(listOf("MY_PLAYLIST", "FOR_YOU"))
            HomeLayoutPreferences.setVisible("QUICK_PICKS", false)
            val saved = HomeLayoutPreferences.layout.value

            HomeLayoutPreferences.install(MapSettings())
            HomeLayoutPreferences.reload()
            assertEquals(HomeLayout(), HomeLayoutPreferences.layout.value)
            HomeLayoutPreferences.install(settings)
            HomeLayoutPreferences.reload()
            assertEquals(saved, HomeLayoutPreferences.layout.value)
            HomeLayoutPreferences.resetSections()
            assertEquals(HomeLayout(playlistsByProfile = saved.playlistsByProfile), HomeLayoutPreferences.layout.value)
        } finally {
            HomeLayoutPreferences.install(MapSettings())
            HomeLayoutPreferences.reload()
        }
    }

    @Test
    fun acceptsYouTubePlaylistLinksAndRejectsLookalikesAndRadioMixes() {
        assertEquals("VLPLabc_123", HomePlaylist.youtubeBrowseId("https://music.youtube.com/playlist?list=PLabc_123&si=share"))
        assertEquals("VLPLabc_123", HomePlaylist.youtubeBrowseId("https://youtu.be/aVideoId?list=PLabc_123"))
        assertNull(HomePlaylist.youtubeBrowseId("https://youtube.com.example.net/playlist?list=PLabc_123"))
        assertNull(HomePlaylist.youtubeBrowseId("https://music.youtube.com/watch?v=aVideoId&list=RDabc"))
        assertNull(HomePlaylist.youtubeBrowseId("https://music.youtube.com/watch?v=aVideoId"))
    }
}
