package com.music.laya.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reserve the footer's reclaimed room for artwork/title instead of absorbing it into transport gaps. */
internal fun portraitControlSlack(slack: Dp, reserve: Dp): Dp =
    (slack - reserve.coerceAtLeast(0.dp)).coerceAtLeast(0.dp)

/** Use the reserved room above the artwork/title, then centre any remaining surplus. */
internal fun portraitArtworkTop(remaining: Dp, reserve: Dp): Dp {
    val room = remaining.coerceAtLeast(0.dp)
    val above = minOf(room, reserve.coerceAtLeast(0.dp))
    return above + (room - above) / 2
}
