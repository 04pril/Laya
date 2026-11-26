package com.music.laya.data.sources

import com.music.laya.data.canvas.normalizeForMatch
import com.music.laya.platform.ConcurrentMap
import com.music.laya.platform.currentTimeMillis
import com.russhwolf.settings.Settings

/**
 * Sources that failed to play a given track, so the next play goes straight to one that works.
 *
 * Qobuz's file of LOVE ATTACK sends one byte and drops the connection, every time: each play spent ten seconds on it
 * before falling over to Tidal. Two lengths of memory:
 *
 * - [record]: any failure in the player, for a day, in memory. A relaunch forgets it, so a passing failure (a dead
 *   proxy, a rate limit) costs nothing beyond the session.
 * - [keep]: a failure proven to be the source's own: the stream never started there, and another source then played
 *   the same track (so the network was fine). Written to the app's settings and kept for two days across launches.
 *
 * Another source's failure, or another track, is unaffected.
 */
object PlaybackFailures {
    private const val SESSION_TTL_MS = 24 * 60 * 60 * 1000L
    private const val KEPT_TTL_MS = 2 * 24 * 60 * 60 * 1000L
    private const val MAX_ENTRIES = 256
    private const val KEY = "playback_failures_v1"

    private class Failure(val at: Long, val kept: Boolean)

    /** Track identity -> config id -> the failure. */
    private val failed = ConcurrentMap<String, Map<String, Failure>>()
    private var store: Settings? = null

    /** Called once settings exist (AppSettings.init): brings back what earlier launches kept. */
    fun install(settings: Settings) {
        store = settings
        val now = currentTimeMillis()
        settings.getStringOrNull(KEY)?.lineSequence()?.forEach { line ->
            val parts = line.split(SEPARATOR)
            if (parts.size != 3) return@forEach
            val at = parts[2].toLongOrNull() ?: return@forEach
            if (now - at !in 0 until KEPT_TTL_MS) return@forEach
            failed[parts[0]] = (failed[parts[0]] ?: emptyMap()) + (parts[1] to Failure(at, kept = true))
        }
    }

    /** [configId] could not play [target] in the player. Skipped for that track for the rest of the day. */
    fun record(configId: String, target: TrackMatcher.Target) {
        val key = keyOf(target) ?: return
        if (failed.size >= MAX_ENTRIES) failed.clear()
        val previous = live(key)?.get(configId)
        failed[key] = (live(key) ?: emptyMap()) + (configId to Failure(currentTimeMillis(), previous?.kept == true))
    }

    /** [configId]'s failure of [target] is its own (another source played it): remembered across launches. */
    fun keep(configId: String, target: TrackMatcher.Target) {
        val key = keyOf(target) ?: return
        failed[key] = (live(key) ?: emptyMap()) + (configId to Failure(currentTimeMillis(), kept = true))
        persist()
    }

    /** The config ids to leave out for [target] now. */
    fun ruledOut(target: TrackMatcher.Target): Set<String> = keyOf(target)?.let { live(it)?.keys } ?: emptySet()

    private fun live(key: String): Map<String, Failure>? {
        val now = currentTimeMillis()
        val entries = failed[key]?.filterValues { now - it.at < if (it.kept) KEPT_TTL_MS else SESSION_TTL_MS } ?: return null
        if (entries.isEmpty()) failed.remove(key) else failed[key] = entries
        return entries.takeIf { it.isNotEmpty() }
    }

    private fun persist() {
        val store = store ?: return
        val now = currentTimeMillis()
        val lines = failed.entries.flatMap { (key, sources) ->
            sources.filter { (_, f) -> f.kept && now - f.at < KEPT_TTL_MS }
                .map { (config, f) -> listOf(key, config, f.at.toString()).joinToString(SEPARATOR) }
        }.takeLast(MAX_ENTRIES)
        if (lines.isEmpty()) store.remove(KEY) else store.putString(KEY, lines.joinToString("\n"))
    }

    private const val SEPARATOR = "\u0001"

    private fun keyOf(target: TrackMatcher.Target): String? {
        val title = CatalogueResults.titleOf(target.title)
        if (title.isBlank()) return null
        return (title + "|" + target.artist.normalizeForMatch()).replace(SEPARATOR, " ").replace("\n", " ")
    }
}
