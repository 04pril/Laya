package com.music.laya.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.laya.ui.theme.stableStatusBarInsets

internal fun tabletPlayerLayoutAvailable(nativeTablet: Boolean, width: Dp, height: Dp): Boolean =
    nativeTablet && width >= 700.dp && height >= 600.dp

internal data class TabletPlayerMetrics(val gutter: Dp, val gap: Dp, val playerWidth: Dp, val playerLeft: Dp, val panelLeft: Dp, val panelWidth: Dp)

internal fun tabletPlayerMetrics(width: Dp, panelOpen: Boolean): TabletPlayerMetrics {
    val gutter = minOf(60.dp, width * 0.05f)
    val gap = gutter
    val inner = (width - gutter * 2).coerceAtLeast(0.dp)
    val panelPlayer = minOf(620.dp, width * 0.46f, (inner - gap).coerceAtLeast(0.dp) * 0.55f)
    val player = if (!panelOpen && width < 900.dp) minOf(580.dp, inner) else panelPlayer
    val left = if (panelOpen) gutter else (width - player) / 2
    return TabletPlayerMetrics(gutter, gap, player, left, gutter + panelPlayer + gap, (inner - panelPlayer - gap).coerceAtLeast(0.dp))
}

internal data class TabletPlayerStack(val artwork: Dp, val top: Dp)

internal fun tabletPlayerStack(height: Dp, width: Dp, controls: Dp): TabletPlayerStack {
    val gap = 24.dp
    // Anchor the deck above the footer instead of leaving all spare height below it.
    val bottom = minOf(64.dp, height * 0.08f)
    val room = (height - controls - gap - bottom).coerceAtLeast(0.dp)
    val artwork = minOf(width, room)
    val top = (height - artwork - controls - gap - bottom).coerceAtLeast(0.dp)
    return TabletPlayerStack(artwork, top)
}

/** Apple Music-style tablet player: one centred deck, or deck left with a live panel right.
 * Playback/lyrics/queue state and components remain owned by NowPlayingScreen. */
@Composable
internal fun TabletPlayerLayout(
    pane: PlayerPane,
    background: @Composable (Modifier) -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    mainPane: @Composable (Boolean, Boolean) -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    portraitHeader: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        background(Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))) {
            if (maxHeight > maxWidth) {
                PortraitTabletPlayer(pane, artwork, actions, mainPane, portraitHeader, lyricsPane, queuePane)
                return@BoxWithConstraints
            }
            val panelOpen = pane != PlayerPane.Main
            val metrics = tabletPlayerMetrics(maxWidth, panelOpen)
            val playerWidth by animateDpAsState(metrics.playerWidth, tween(320), label = "tabletPlayerWidth")
            val left by animateDpAsState(metrics.playerLeft, tween(320), label = "tabletPlayerLeft")
            val compact = maxHeight < 760.dp
            Column(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 8.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Remember natural control height so a height-limited sleeve and its
                    // controls use the same width on the following measure pass.
                    var controlsHeight by remember { mutableIntStateOf(0) }
                    Layout(
                        content = {
                            Box { artwork(Modifier.fillMaxSize()) }
                            Box(Modifier.fillMaxWidth()) { mainPane(compact, false) }
                        },
                        modifier = Modifier.offset(x = left).width(playerWidth).fillMaxHeight(),
                    ) { children, constraints ->
                        val controlWidth = if (controlsHeight > 0) {
                            tabletPlayerStack(constraints.maxHeight.toDp(), constraints.maxWidth.toDp(), controlsHeight.toDp())
                                .artwork.roundToPx().coerceAtLeast(280.dp.roundToPx()).coerceAtMost(constraints.maxWidth)
                        } else constraints.maxWidth
                        val controls = children[1].measure(Constraints.fixedWidth(controlWidth))
                        if (controlsHeight != controls.height) controlsHeight = controls.height
                        val stack = tabletPlayerStack(constraints.maxHeight.toDp(), constraints.maxWidth.toDp(), controls.height.toDp())
                        val side = stack.artwork.roundToPx()
                        val art = children[0].measure(Constraints.fixed(side, side))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val top = stack.top.roundToPx()
                            art.placeRelative((constraints.maxWidth - side) / 2, top)
                            controls.placeRelative((constraints.maxWidth - controls.width) / 2, top + side + 24.dp.roundToPx())
                        }
                    }
                    TabletSidePanel(panelOpen, pane, lyricsPane, queuePane,
                        Modifier.offset(x = metrics.panelLeft).width(metrics.panelWidth).fillMaxHeight())
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = metrics.gutter)) { actions() }
            }
            Box(Modifier.align(Alignment.TopCenter).offset(y = 8.dp).size(38.dp, 5.dp)
                .clip(RoundedCornerShape(3.dp)).background(LocalPlayerTint.current.copy(alpha = 0.7f)))
        }
    }
}

@Composable
private fun TabletSidePanel(
    visible: Boolean, pane: PlayerPane,
    lyrics: @Composable () -> Unit, queue: @Composable () -> Unit,
    modifier: Modifier,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(240, 80)), exit = fadeOut(tween(160)), modifier = modifier) {
        Box(Modifier.fillMaxSize().padding(start = PLAYER_GUTTER, end = if (pane == PlayerPane.Lyrics) 0.dp else PLAYER_GUTTER)) {
            when (pane) {
                PlayerPane.Lyrics -> lyrics()
                PlayerPane.Queue -> queue()
                PlayerPane.Main -> Unit
            }
        }
    }
}

/** Portrait uses a full-width deck; opening a panel replaces the sleeve with a
 * compact header instead of squeezing landscape columns into a tall window. */
@Composable
private fun PortraitTabletPlayer(
    pane: PlayerPane,
    artwork: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    mainPane: @Composable (Boolean, Boolean) -> Unit,
    header: @Composable () -> Unit,
    lyrics: @Composable () -> Unit,
    queue: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = minOf(64.dp, maxWidth * 0.065f)
        val compact = maxHeight < 1000.dp
        val panelOpen = pane != PlayerPane.Main
        Column(Modifier.fillMaxSize().padding(top = 64.dp, bottom = 8.dp)) {
            if (panelOpen) {
                Row(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    artwork(Modifier.size(64.dp))
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.weight(1f)) { header() }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = gutter)) {
                    if (pane == PlayerPane.Lyrics) lyrics() else queue()
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(700.dp, maxWidth * 0.74f, (maxHeight - 48.dp).coerceAtLeast(0.dp))
                    artwork(Modifier.size(side))
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { mainPane(compact, panelOpen) }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().padding(horizontal = gutter)) { actions() }
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = 8.dp).size(38.dp, 5.dp)
            .clip(RoundedCornerShape(3.dp)).background(LocalPlayerTint.current.copy(alpha = 0.7f)))
    }
}
