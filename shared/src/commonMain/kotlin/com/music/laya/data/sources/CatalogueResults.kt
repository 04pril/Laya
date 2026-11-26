package com.music.laya.data.sources

import com.music.laya.data.canvas.normalizeForMatch
import com.music.laya.data.canvas.splitArtists
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
        val representatives = ArrayList<Song>()
        perSource.forEach { rows ->
            rows.forEach { song -> if (representatives.none { sameRecording(it, song) }) representatives += song }
        }
        val kept = representatives.mapTo(HashSet()) { it.videoId }
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

    /**
     * One recording listed by two catalogues. Soundtracks and classical are credited differently from one to the
     * next: Qobuz's "Across the Stars (…) (From "Star Wars…"/Score)" by John Williams is Tidal's "Across the Stars (…)"
     * by John Williams, London Symphony Orchestra. So: the title without a source note ("(From …)", "(… Score)",
     * soundtrack/OST tags), one side's artists all among the other's, explicitness not contradicting, and lengths
     * within five seconds. A different cut ("(Inst.)", "(Remix)", a live take) keeps its own row.
     */
    internal fun sameRecording(a: Song, b: Song): Boolean {
        if (a.isExplicit != null && b.isExplicit != null && a.isExplicit != b.isExplicit) return false
        if (!sameLength(a, b)) return false
        if (titleOf(a.title) != titleOf(b.title)) return false
        val x = splitArtists(a.artist).toSet()
        val y = splitArtists(b.artist).toSet()
        if (x.isEmpty() || y.isEmpty()) return x == y
        return x.containsAll(y) || y.containsAll(x)
    }

    internal fun titleOf(title: String): String = title.replace(SOURCE_NOTE, " ").normalizeForMatch()

    /** "(From "…")", "[From …]", "(… Score)", "(Original Motion Picture Soundtrack)", "(OST)": where it is from, not what. */
    private val SOURCE_NOTE = Regex(
        """\s*[(\[]\s*(?:taken\s+)?from\b[^)\]]*[)\]]|\s*[(\[][^)\]]*\b(?:score|soundtrack|ost)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )

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
