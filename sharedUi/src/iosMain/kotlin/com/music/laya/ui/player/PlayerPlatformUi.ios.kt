package com.music.laya.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.music.laya.ios.IosScreenAwake
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.music.laya.platform.nanoTime
import com.music.laya.platform.systemLanguageTag
import platform.UIKit.UIApplication

internal actual fun uptimeMillis(): Long = nanoTime() / 1_000_000L

// Skia blurs at every size.
internal actual val renderEffectBlurSupported: Boolean = true

@Composable
internal actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        if (enabled) IosScreenAwake.lyrics(true)
        onDispose { if (enabled) IosScreenAwake.lyrics(false) }
    }
}

@Composable
internal actual fun appLanguageTag(): String = com.music.laya.data.settings.IosCustomization.language.collectAsState().value

// ponytail: iOS has no back button; the sheet dismisses by swipe. Edge-swipe back is 4b's shell job.
@Composable
internal actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

// A finger both scrolls and drags, as on Android.
internal actual val drawerFollowsListScroll: Boolean get() = true

// Android's plain clip + translate; Skia on iOS draws at device-pixel precision already.
internal actual fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) = clipRect(left = left, top = top, right = right, bottom = bottom) { translate(top = dy) { block() } }
