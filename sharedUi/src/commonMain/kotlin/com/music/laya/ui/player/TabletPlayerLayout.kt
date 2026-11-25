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
    val panelPlayer = minOf(580.dp, width * 0.42f, (inner - gap).coerceAtLeast(0.dp) / 2)
    val player = if (!panelOpen && width < 900.dp) minOf(580.dp, inner) else panelPlayer
    val left = if (panelOpen) gutter else (width - player) / 2
    return TabletPlayerMetrics(gutter, gap, player, left, gutter + panelPlayer + gap, (inner - panelPlayer - gap).coerceAtLeast(0.dp))
}

internal data class TabletPlayerStack(val artwork: Dp, val top: Dp)

internal fun tabletPlayerStack(height: Dp, width: Dp, controls: Dp): TabletPlayerStack {
    val gap = 24.dp
    val room = (height - controls - gap).coerceAtLeast(0.dp)
    val artwork = minOf(width, room)
    val top = minOf(height * 0.08f, (height - artwork - controls - gap).coerceAtLeast(0.dp))
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
    mainPane: @Composable (Boolean) -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        background(Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.union(stableStatusBarInsets()))) {
            val panelOpen = pane != PlayerPane.Main
            val metrics = tabletPlayerMetrics(maxWidth, panelOpen)
            val playerWidth by animateDpAsState(metrics.playerWidth, tween(320), label = "tabletPlayerWidth")
            val left by animateDpAsState(metrics.playerLeft, tween(320), label = "tabletPlayerLeft")
            val compact = maxHeight < 760.dp
            Column(Modifier.fillMaxSize().padding(top = 40.dp, bottom = 8.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // Measure controls first so the artwork only uses the room left over.
                    // No estimated heights or second-frame feedback when lyrics appear.
                    Layout(
                        content = {
                            Box { artwork(Modifier.fillMaxSize()) }
                            Box(Modifier.fillMaxWidth()) { mainPane(compact) }
                        },
                        modifier = Modifier.offset(x = left).width(playerWidth).fillMaxHeight(),
                    ) { children, constraints ->
                        val controls = children[1].measure(Constraints.fixedWidth(constraints.maxWidth))
                        val stack = tabletPlayerStack(constraints.maxHeight.toDp(), constraints.maxWidth.toDp(), controls.height.toDp())
                        val side = stack.artwork.roundToPx()
                        val art = children[0].measure(Constraints.fixed(side, side))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val top = stack.top.roundToPx()
                            art.placeRelative((constraints.maxWidth - side) / 2, top)
                            controls.placeRelative(0, top + side + 24.dp.roundToPx())
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
