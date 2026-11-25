package com.music.laya.ui.player

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.*

class CustomLyricsFontTest {
    @Test fun tabletDefaultEnlargesFullLyricsButPreservesStripAndSavedSizes() {
        assertEquals(48f / 34f, lyricFontScale(34f, 48, false))
        assertEquals(1f, lyricFontScale(16f, 48, false))
        assertEquals(28f / 34f, lyricFontScale(34f, 28, true))
        assertEquals(28f / 34f, lyricFontScale(16f, 28, true))
    }

    @Test fun automaticLineHeightRemainsAutomaticAtEverySupportedFontSize() {
        // The one-line lyric strip supplies no lineHeight. Its first timed lyric crashed build 38.
        val strip = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        for (size in 18..52) {
            val result = scaledLyricLineHeight(strip, size / 34f)
            assertEquals(TextUnit.Unspecified, result.lineHeight)
            assertEquals(strip.fontSize, result.fontSize)
            assertEquals(strip.fontWeight, result.fontWeight)
        }
    }

    @Test fun explicitFullPanelLineHeightScalesWithFontSize() {
        val fullPanel = TextStyle(fontSize = 34.sp, lineHeight = 41.sp)
        val result = scaledLyricLineHeight(fullPanel, 1.5f)
        assertEquals(61.5.sp, result.lineHeight)
        assertEquals(fullPanel.fontSize, result.fontSize)
    }

    @Test fun relativeLineHeightRetainsItsUnit() {
        val result = scaledLyricLineHeight(TextStyle(lineHeight = 1.2.em), 2f)
        assertEquals(2.4.em, result.lineHeight)
    }

    @Test fun invalidScaleLeavesOriginalStyleIntact() {
        val style = TextStyle(lineHeight = 24.sp)
        for (scale in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1f)) {
            assertEquals(style, scaledLyricLineHeight(style, scale))
        }
    }
}
