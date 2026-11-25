package com.music.laya.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PortraitPlayerSpacingTest {
    private data class Layout(val titleTop: Dp, val spread: Dp)
    private fun layout(naturalRoom: Dp, reserve: Dp): Layout {
        val width = 333.dp
        val titleBlock = 80.dp
        val art = minOf(width, naturalRoom - titleBlock)
        val slack = (naturalRoom - art - titleBlock).coerceAtLeast(0.dp)
        val spread = portraitControlSlack(slack, reserve).coerceAtMost(48.dp)
        val groupTop = portraitArtworkTop(naturalRoom - spread - art - titleBlock, reserve)
        return Layout(groupTop + art + 20.dp, spread)
    }

    @Test fun reclaimedFooterRoomLowersTitleRatherThanInflatingTransportGaps() {
        // Height-bound phone, width-bound phone, and tall phone already at the spread cap.
        for (height in listOf(383.dp, 413.dp, 483.dp)) {
            val before = layout(height, 0.dp)
            val after = layout(height + 26.dp, 26.dp)
            assertEquals(before.titleTop + 26.dp, after.titleTop)
            assertEquals(before.spread, after.spread)
        }
    }

    @Test fun compactOrTransientMeasurementsNeverProduceNegativeSpace() {
        assertEquals(0.dp, portraitControlSlack(10.dp, 26.dp))
        assertEquals(0.dp, portraitArtworkTop((-20).dp, 26.dp))
        assertEquals(10.dp, portraitArtworkTop(10.dp, 26.dp))
        assertEquals(10.dp, portraitArtworkTop(20.dp, 0.dp))
    }
}
