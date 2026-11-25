package com.music.laya.ui.player

/** A live swap needs the selected track to be ready in the engine, not merely present in the queue. */
internal enum class VersionPlaybackMode { START, SWAP }

internal fun handoffPlayerVersion(
    selectedId: String,
    openedId: String?,
    loading: Boolean,
    cancelPendingStart: () -> Unit,
    hand: (VersionPlaybackMode) -> Unit,
) {
    val mode = if (loading || openedId != selectedId) VersionPlaybackMode.START else VersionPlaybackMode.SWAP
    // Otherwise a slower resolve of the original video can overwrite the chosen audio version.
    cancelPendingStart()
    hand(mode)
}
