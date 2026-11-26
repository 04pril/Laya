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
    class Insets(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        /** The status column is a wide side inset; a notch in landscape on an ordinary phone never reaches here. */
        val active: Boolean get() = maxOf(left, right) >= 40.0
        val railOnRight: Boolean get() = right >= left
        val padding: PaddingValues get() = PaddingValues(left.dp, top.dp, right.dp, bottom.dp)
    }

    val insets = mutableStateOf(Insets(0.0, 0.0, 0.0, 0.0))

    /** From StatusBarHost on every layout, in points; zeros when the view is not inset. */
    fun update(left: Double, top: Double, right: Double, bottom: Double) {
        val now = insets.value
        if (now.left != left || now.top != top || now.right != right || now.bottom != bottom) {
            insets.value = Insets(left, top, right, bottom)
        }
    }
}
