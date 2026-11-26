package com.music.laya.data.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasMatchTest {
    private fun hit(title: String, artist: String, album: String? = null) =
        CanvasArtwork(url = "https://example.invalid/c.mp4", title = title, artist = artist, album = album)

    @Test
    fun latinFoldingStillWorks() {
        assertEquals("beyonce crazy in love feat jay z", "Beyoncé - CRAZY IN LOVE (feat. JAY-Z)".normalizeForMatch())
    }

    @Test
    fun hangulIsKept() {
        assertTrue("아이유".normalizeForMatch().isNotBlank())
        assertEquals("밤편지".normalizeForMatch(), "밤편지!".normalizeForMatch())
    }

    @Test
    fun koreanArtistMatches() {
        assertTrue(hit("밤편지", "아이유").matches("밤편지", "아이유", null))
    }

    @Test
    fun differentKoreanTitlesNoLongerCompareEqual() {
        assertFalse(hit("좋은 날", "아이유").matches("밤편지", "아이유", null))
    }

    @Test
    fun japaneseArtistMatches() {
        assertTrue(hit("夜に駆ける", "YOASOBI").matches("夜に駆ける", "YOASOBI", null))
        assertTrue(hit("マリーゴールド", "あいみょん").matches("マリーゴールド", "あいみょん", null))
    }
}
