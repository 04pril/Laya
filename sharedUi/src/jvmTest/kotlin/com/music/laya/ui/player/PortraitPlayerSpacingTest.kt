package com.music.laya.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.laya.data.settings.MixBlend
import com.music.laya.ui.theme.LayaTheme
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
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

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun transportIsCenteredBetweenTheTracksAtDifferentTextSizes() {
        val settings = Proxy.newProxyInstance(
            PlayerSettingsSource::class.java.classLoader, arrayOf(PlayerSettingsSource::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getSmartMixBlend" -> MutableStateFlow<MixBlend?>(null)
                "getReduceAnimation" -> MutableStateFlow(true)
                else -> error("Unexpected player setting ${method.name}")
            }
        } as PlayerSettingsSource
        val host = Proxy.newProxyInstance(PlayerHost::class.java.classLoader, arrayOf(PlayerHost::class.java)) { _, method, _ ->
            check(method.name == "getSettings")
            settings
        } as PlayerHost
        val previousHost = runCatching { PlayerPlatform.host }.getOrNull()
        PlayerPlatform.install(host)
        try {
            for (fontScale in listOf(1f, 1.6f, 2f)) {
                for (showVolume in listOf(true, false)) {
                    val bounds = mutableMapOf<String, Rect>()
                    fun record(name: String) = Modifier.onGloballyPositioned { bounds[name] = it.boundsInRoot() }
                    val scene = ImageComposeScene(332, 400, Density(1f, fontScale)) {
                        LayaTheme(darkTheme = true) {
                            PortraitPlaybackControls(
                                gap = 30.dp,
                                scrubber = {
                                    Box(record("seek")) {
                                        PlayerScrubber({ 0.4f }, 180_000, false, false, null, {}, {})
                                    }
                                },
                                transport = {
                                    Box(record("buttons")) {
                                        TransportRow(false, false, true, true, {}, {}, {}, portraitPhone = true)
                                    }
                                },
                                volume = {
                                    Box(record("volume")) {
                                        if (showVolume) VolumeRow({ 0.5f }, {}, {})
                                        else Box(Modifier.fillMaxWidth().height(VOLUME_ROW_HEIGHT))
                                    }
                                },
                            )
                        }
                    }
                    try {
                        repeat(3) { scene.render(it * 100_000_000L).close() }
                    } finally {
                        scene.close()
                    }
                    // The default seek slider's 34dp touch target is centered 17dp below its top.
                    val seekTrack = bounds.getValue("seek").top + 17f
                    val volumeTrack = bounds.getValue("volume").center.y
                    assertEquals((seekTrack + volumeTrack) / 2f, bounds.getValue("buttons").center.y, absoluteTolerance = 1f)
                }
            }
        } finally {
            previousHost?.let(PlayerPlatform::install)
        }
    }
}
