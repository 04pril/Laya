package com.music.laya.data.sources

import com.music.laya.data.MonotonicClock
import com.music.laya.data.canvas.normalizeForMatch
import com.music.laya.platform.ConcurrentMap

/**
 * Sources that failed to play a given track, remembered for a day, so the next play goes straight to one that works.
 *
 * Qobuz's file of LOVE ATTACK sends one byte and drops the connection, every time: each play spent ten seconds on it
 * before falling over to Tidal. A source that failed a track in the player is skipped for that track until the entry
 * expires; another source's failure, or another track, is unaffected. In memory only: a relaunch gives every source
 * its chance again, which is also what lets a fixed file be noticed.
 */
object PlaybackFailures {
    private const val TTL_MS = 24 * 60 * 60 * 1000L
    private const val MAX_ENTRIES = 256

    /** Track identity -> config id -> when it failed. */
    private val failed = ConcurrentMap<String, Map<String, Long>>()

    /** [configId] could not play [target] (its stream failed in the player, not merely missing from the catalogue). */
    fun record(configId: String, target: TrackMatcher.Target) {
        val key = keyOf(target) ?: return
        if (failed.size >= MAX_ENTRIES) failed.clear()
        failed[key] = (live(key) ?: emptyMap()) + (configId to MonotonicClock.nowMs())
    }

    /** The config ids to leave out for [target] now. */
    fun ruledOut(target: TrackMatcher.Target): Set<String> = keyOf(target)?.let { live(it)?.keys } ?: emptySet()

    private fun live(key: String): Map<String, Long>? {
        val now = MonotonicClock.nowMs()
        val entries = failed[key]?.filterValues { now - it < TTL_MS } ?: return null
        if (entries.isEmpty()) failed.remove(key) else failed[key] = entries
        return entries.takeIf { it.isNotEmpty() }
    }

    private fun keyOf(target: TrackMatcher.Target): String? {
        val title = CatalogueResults.titleOf(target.title)
        if (title.isBlank()) return null
        return title + "|" + target.artist.normalizeForMatch()
    }
}
