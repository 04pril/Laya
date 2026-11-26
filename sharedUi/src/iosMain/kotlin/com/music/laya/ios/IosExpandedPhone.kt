package com.music.laya.ios

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp

/**
 * An unfolded phone (iPhone Duo). StatusBarHost used to shrink the whole Compose view to the window's safe
 * rectangle there, which left the side status column an empty black strip. It now keeps the view full size and
 * hands that rectangle here instead: the app pads itself by it (the same bounds as before) and puts its tabs in
 * the side strip, where BitChord's system tab bar sits on the same device.
 */
object IosExpandedPhone {
    class Insets(val left: Double, val top: Double, val right: Double, val bottom: Double, val unfolded: Boolean = false) {
        /**
         * The Duo's status column: a wide inset on one side only, folded or open, either way up. An ordinary
         * iPhone has no side inset upright and an even pair on its side (the notch and the home side), so it
         * never matches; nor does an iPad.
         */
        val active: Boolean get() = maxOf(left, right) >= 40.0 && minOf(left, right) < 20.0
        val railOnRight: Boolean get() = right >= left
        /**
         * What the app keeps clear of: the status column's side and the side across from it, while [active].
         * Top and bottom stay with Compose's own insets (pages already keep under the status bar where they should).
         */
        val padding: PaddingValues get() =
            if (active) PaddingValues(start = left.dp, end = right.dp) else PaddingValues(0.dp)
    }

    val insets = mutableStateOf(Insets(0.0, 0.0, 0.0, 0.0))

    /** From StatusBarHost on every layout of an iPhone: the window's safe insets, in points; zeros elsewhere. */
    /** [unfolded]: the window's short side is 600 pt or more (the Duo open; folded it is a phone-sized screen). */
    fun update(left: Double, top: Double, right: Double, bottom: Double, unfolded: Boolean) {
        val now = insets.value
        if (now.left != left || now.top != top || now.right != right || now.bottom != bottom || now.unfolded != unfolded) {
            insets.value = Insets(left, top, right, bottom, unfolded)
        }
    }
}
