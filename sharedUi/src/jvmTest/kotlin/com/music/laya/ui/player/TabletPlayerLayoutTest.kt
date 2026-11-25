package com.music.laya.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.music.laya.ui.theme.LayaTheme
import com.music.laya.data.model.Song
import com.music.laya.data.model.LikeStatus
import com.music.laya.data.lyrics.LyricLine
import com.music.laya.playback.PlaybackPosition
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.lang.reflect.Proxy
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class TabletPlayerLayoutTest {
    @Test fun layoutTracksWindowSizeWithoutUsingTabletLayoutOnPhonesOrNarrowWindows() {
        assertFalse(tabletPlayerLayoutAvailable(false, 1366.dp, 1024.dp))
        assertFalse(tabletPlayerLayoutAvailable(true, 600.dp, 1194.dp))
        assertTrue(tabletPlayerLayoutAvailable(true, 834.dp, 1194.dp))
        assertFalse(tabletPlayerLayoutAvailable(true, 1024.dp, 500.dp))
        assertTrue(tabletPlayerLayoutAvailable(true, 1366.dp, 1024.dp))
        assertTrue(tabletPlayerLayoutAvailable(true, 1024.dp, 1366.dp))
        for (width in listOf(768.dp, 834.dp, 900.dp, 1024.dp, 1280.dp, 1366.dp)) {
            val closed = tabletPlayerMetrics(width, false)
            val open = tabletPlayerMetrics(width, true)
            assertEquals((width - closed.playerWidth) / 2, closed.playerLeft)
            if (width >= 900.dp) assertEquals(closed.playerWidth, open.playerWidth)
            else assertTrue(closed.playerWidth > open.playerWidth)
            assertTrue(open.playerLeft + open.playerWidth < open.panelLeft)
            assertTrue(open.panelLeft + open.panelWidth <= width - open.gutter)
            for (height in listOf(380.dp, 600.dp, 900.dp)) {
                val stack = tabletPlayerStack(height, open.playerWidth, 250.dp)
                assertTrue(stack.artwork >= 0.dp && stack.top >= 0.dp)
                if (height >= 274.dp) assertTrue(stack.top + stack.artwork + 24.dp + 250.dp <= height)
            }
        }
    }

    @Test fun renderRealTabletLayoutKeepsArtworkControlsAndLyricStripTogether() {
        // Use the same Compose/Skia view tree as iOS, without a playback/network engine.
        val settings = Proxy.newProxyInstance(PlayerSettingsSource::class.java.classLoader, arrayOf(PlayerSettingsSource::class.java)) { _, method, _ ->
            when (method.name) {
                "getLyricsOffsetMs" -> MutableStateFlow(0)
                "getSmartMixBlend" -> MutableStateFlow<com.music.laya.data.settings.MixBlend?>(null)
                else -> MutableStateFlow(false)
            }
        } as PlayerSettingsSource
        val host = Proxy.newProxyInstance(PlayerHost::class.java.classLoader, arrayOf(PlayerHost::class.java)) { _, method, _ ->
            when (method.name) {
                "getSettings" -> settings
                else -> null
            }
        } as PlayerHost
        PlayerPlatform.install(host)
        for ((width, height) in listOf(1280 to 960, 1024 to 1366, 1024 to 768, 834 to 1194, 768 to 1024)) {
            for (pane in PlayerPane.entries) {
                val bounds = mutableMapOf<String, Rect>()
                val scene = ImageComposeScene(width, height, Density(1f)) {
                    LayaTheme(darkTheme = true) {
                        TabletPlayerLayout(
                            pane = pane,
                            background = { Box(it.background(Brush.verticalGradient(listOf(Color(0xff455059), Color(0xff11151a))))) },
                            artwork = { m -> Box(m.onGloballyPositioned { bounds["art"] = it.boundsInRoot() }
                                .background(Brush.linearGradient(listOf(Color(0xff87929c), Color(0xff222e3b))))) },
                            actions = { Row(Modifier.fillMaxWidth().height(48.dp).onGloballyPositioned { bounds["actions"] = it.boundsInRoot() }, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Output · AirPods Pro", color = Color.White)
                                Text("Autoplay     Lyrics     Queue", color = Color.White)
                            } },
                            mainPane = { compact ->
                                Box(Modifier.onGloballyPositioned { bounds["controls"] = it.boundsInRoot() }) {
                                    LandscapeMainPane(compact, null, {},
                                        credits = { LandscapeCredits(Song("preview", "Snowman", "Sia", null), false, LikeStatus.INDIFFERENT,
                                            false, {}, {}, {}, { _, _ -> }) },
                                        lyricStrip = if (pane != PlayerPane.Lyrics) ({
                                            Box(Modifier.onGloballyPositioned { bounds["strip"] = it.boundsInRoot() }) {
                                                CurrentLyricStrip(listOf(LyricLine(0, "Don't cry, Snowman"), LyricLine(1000, "Who will catch your tears")),
                                                    "preview", LyricPlayhead(remember { PlaybackPosition().apply { report(1500) } }, mutableStateOf(0)),
                                                    false, 180000, false, "Loading lyrics", {})
                                            }
                                        }) else null,
                                        scrubber = { Box(Modifier.onGloballyPositioned { bounds["scrubber"] = it.boundsInRoot() }) {
                                            PlayerScrubber({ 0.17f }, 180000, false, false, null, {}, {})
                                        } },
                                        transport = { TabletTransportControls(false, 0, {}, {}) { TransportRow(true, false, true, true, {}, {}, {}, compact) } },
                                        volume = { Box(Modifier.onGloballyPositioned { bounds["volume"] = it.boundsInRoot() }) { VolumeRow({ 0.6f }, {}, {}) } },
                                        maxContentWidth = 600.dp,
                                        lyricStripBottomGap = 12.dp,
                                    )
                                }
                            },
                            lyricsPane = { Column(Modifier.fillMaxSize().onGloballyPositioned { bounds["panel"] = it.boundsInRoot() }, verticalArrangement = Arrangement.Center) {
                                for ((i, text) in listOf("Don't cry, Snowman", "Who will catch your tears", "If you can't catch me, darling?").withIndex()) {
                                    Text(text, color = Color.White.copy(alpha = if (i == 1) 1f else 0.35f), fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 22.dp))
                                }
                            } },
                            queuePane = { Column(Modifier.fillMaxSize().onGloballyPositioned { bounds["panel"] = it.boundsInRoot() }) {
                                Text("Up Next", fontSize = 26.sp, color = Color.White)
                                repeat(6) { Text("Track ${it + 1}     ·     Artist", fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(vertical = 20.dp)) }
                            } },
                        )
                    }
                }
                try {
                    repeat(12) { scene.render((it + 1) * 100_000_000L).close() }
                    val art = bounds.getValue("art"); val controls = bounds.getValue("controls"); val actions = bounds.getValue("actions")
                    assertTrue(art.bottom <= controls.top, "Artwork must precede credits/transport: $bounds")
                    assertTrue(controls.bottom <= actions.top, "Controls must stay above the footer: $bounds")
                    assertTrue(art.width >= 280 && art.height == art.width)
                    assertTrue(controls.width >= 300 && controls.height >= 180)
                    assertTrue(bounds.getValue("volume").bottom <= actions.top, "The volume row must remain visible above the footer: $bounds")
                    bounds["strip"]?.let { strip ->
                        assertTrue(bounds.getValue("scrubber").top - strip.bottom >= 12f, "One-line lyrics need breathing room above the scrubber: $bounds")
                    }
                    if (pane == PlayerPane.Main) {
                        assertEquals(width / 2f, art.center.x, 2f)
                        assertNotNull(bounds["strip"], "Main mode retains the existing one-line lyrics component")
                        assertNull(bounds["panel"])
                    } else {
                        assertTrue(art.center.x < width / 2f)
                        assertTrue(bounds.getValue("panel").left >= art.right)
                        if (pane == PlayerPane.Queue) assertNotNull(bounds["strip"])
                    }
                    val dir = File("build/tablet-player-proof").apply { mkdirs() }
                    val image = scene.render(2_000_000_000L)
                    File(dir, "$width-$height-${pane.name.lowercase()}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                    image.close()
                } finally { scene.close() }
            }
        }
    }
}
