package com.music.laya.ios

import platform.UIKit.UIApplication

/** One owner combines lyrics and CarPlay so disposing a lyric view cannot undo the car policy. */
object IosScreenAwake {
    private var lyrics = false
    private var car = false
    fun lyrics(keep: Boolean) { lyrics = keep; update() }
    fun car(keep: Boolean) { car = keep; update() }
    private fun update() { UIApplication.sharedApplication.idleTimerDisabled = lyrics || car }
}
