package com.music.laya.data.sources

import com.music.laya.data.canvas.normalizeForMatch
import com.music.laya.data.model.Song
import kotlin.math.abs

/**
 * The Addons search tab's list: every catalogue's answers interleaved, the same recording listed once.
 *
 * With Qobuz and Tidal both on, each track came up twice, and which row was tapped decided which source it was tied
 * to. The source order is already the user's statement of which they want first, and a source that fails now hands
 * the track on to the next one (SourceResolver.otherSourceFor), so a second row asks a question nobody needs to
 * answer. Kept: the row from the source ranked highest.
 */
object CatalogueResults {
    /** [perSource] in the user's source order, each list in that source's own ranking. */
    fun merge(perSource: List<List<Song>>): List<Song> {
        // Representatives chosen in source order: the best-ranked source's row for each recording.
        val byKey = HashMap<String, MutableList<Song>>()
        val kept = HashSet<String>() // videoIds of the representatives
        perSource.forEach { rows ->
            rows.forEach { song ->
                val group = byKey.getOrPut(keyOf(song)) { mutableListOf() }
                if (group.none { sameLength(it, song) }) { group += song; kept += song.videoId }
            }
        }
        // Round-robin, so no single catalogue buries the others.
        val shown = HashSet<String>()
        return buildList {
            for (i in 0 until (perSource.maxOfOrNull { it.size } ?: 0)) {
                perSource.forEach { rows ->
                    val song = rows.getOrNull(i) ?: return@forEach
                    if (song.videoId in kept && shown.add(song.videoId)) add(song)
                }
            }
        }
    }

    /** Same title, artist and explicitness. A clean and an explicit cut stay apart; length is compared after. */
    internal fun keyOf(song: Song): String =
        listOf(song.title.normalizeForMatch(), song.artist.normalizeForMatch(), song.isExplicit.toString()).joinToString("|")

    /** Within five seconds when both state a length (3:01 on Qobuz is 3:02 on Tidal); a single and its long mix differ. */
    internal fun sameLength(a: Song, b: Song): Boolean {
        val x = a.durationText?.let(::seconds) ?: return true
        val y = b.durationText?.let(::seconds) ?: return true
        return abs(x - y) <= 5
    }

    private fun seconds(text: String): Int? {
        val parts = text.split(':').map { it.trim().toIntOrNull() ?: return null }
        return parts.fold(0) { acc, part -> acc * 60 + part }
    }
}
