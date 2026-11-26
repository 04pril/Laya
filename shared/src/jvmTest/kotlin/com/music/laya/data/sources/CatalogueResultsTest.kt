package com.music.laya.data.sources

import com.music.laya.data.model.Song
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogueResultsTest {
    private fun song(id: String, title: String, artist: String, duration: String? = null, explicit: Boolean? = false) =
        Song(videoId = id, title = title, artist = artist, thumbnailUrl = null, durationText = duration, isExplicit = explicit, fromAutoplay = false)

    @Test
    fun `the same recording from Qobuz and Tidal is listed once, from the source ranked first`() {
        val qobuz = listOf(song("q1", "LOVE ATTACK", "RESCENE", "3:01"), song("q2", "Other", "RESCENE", "3:30"))
        val tidal = listOf(song("t1", "Love Attack", "RESCENE", "3:02"), song("t2", "Third", "RESCENE", "2:50"))
        assertEquals(listOf("q1", "q2", "t2"), CatalogueResults.merge(listOf(qobuz, tidal)).map { it.videoId })
    }

    @Test
    fun `the user's order decides which copy stays`() {
        val tidal = listOf(song("t1", "LOVE ATTACK", "RESCENE", "3:02"))
        val qobuz = listOf(song("q1", "LOVE ATTACK", "RESCENE", "3:01"))
        assertEquals(listOf("t1"), CatalogueResults.merge(listOf(tidal, qobuz)).map { it.videoId })
    }

    @Test
    fun `a different length, an explicit cut or another artist is another recording`() {
        val a = listOf(song("a1", "Song", "X", "3:00"), song("a2", "Song", "X", "3:00", explicit = true))
        val b = listOf(song("b1", "Song", "X", "6:12"), song("b2", "Song", "Y", "3:00"))
        assertEquals(listOf("a1", "b1", "a2", "b2"), CatalogueResults.merge(listOf(a, b)).map { it.videoId })
    }

    @Test
    fun `korean titles are compared, not erased`() {
        val a = listOf(song("a1", "밤편지", "아이유", "4:13"))
        val b = listOf(song("b1", "좋은 날", "아이유", "3:53"), song("b2", "밤편지", "아이유", "4:14"))
        assertEquals(listOf("a1", "b1"), CatalogueResults.merge(listOf(a, b)).map { it.videoId })
    }

    @Test
    fun `a soundtrack credited differently by each catalogue is one recording`() {
        val qobuz = listOf(song("q", "Across the Stars (Love Theme from \"Star Wars: Attack of the Clones\") (From \"Star Wars: Attack of the Clones\"/Score)", "John Williams", "5:31"))
        val tidal = listOf(song("t", "Across the Stars (Love Theme from \"Star Wars: Attack of the Clones\")", "John Williams, London Symphony Orchestra", "5:32"))
        assertEquals(listOf("q"), CatalogueResults.merge(listOf(qobuz, tidal)).map { it.videoId })
    }

    @Test
    fun `an instrumental or a remix keeps its own row`() {
        val a = listOf(song("a", "연애소설 (Love Soseol)", "tripleS", "2:59"))
        val b = listOf(song("b", "연애소설 (Love Soseol) (Inst.)", "tripleS", "2:58"), song("c", "Love Attack (Moods Remix)", "Fhin", "2:54"))
        assertEquals(listOf("a", "b", "c"), CatalogueResults.merge(listOf(a, b)).map { it.videoId })
    }
}
