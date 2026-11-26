package com.music.laya.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** Actual Media3 Auto/Automotive controllers, not Bluetooth audio pairing. */
object CarConnectionState { val connected = MutableStateFlow(false) }
