package com.music.laya.data.sources.lossless

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/** The DASH audio subset used by Tidal and by Android's offline source downloads. */
object DashAudio {
    private const val MAX_SEGMENTS = 4096

    /** One audio rendition's segments, resolved from the template into plain URLs. */
    class Plan(
        val initialization: String,
        val media: List<String>,
        val seconds: List<Double>,
    )

    /**
     * The segment list a manifest describes.
     *
     * Read with regexes rather than an XML parser, and narrowly: this is not a
     * general DASH implementation and should not read like one. It handles the
     * single-period, single-audio-representation, `SegmentTemplate` manifests
     * the source modules and built-in Tidal actually serve.
     */
    fun parse(manifest: String, requireFlac: Boolean = false): Plan {
        require(manifest.length <= 256 * 1024) { "DASH manifest is too large" }
        if (Regex("<ContentProtection", RegexOption.IGNORE_CASE).containsMatchIn(manifest)) {
            error("Encrypted DASH cannot be saved")
        }
        if (Regex("<Period[\\s>]").findAll(manifest).count() > 1) {
            error("Multi-period DASH cannot be saved")
        }
        // Tidal lists every rendition of the track in one manifest — HE-AAC,
        // then AAC-LC, then FLAC — and reading the first SegmentTemplate in the
        // file took HE-AAC every time: 97kbps saved under a FLAC badge. The
        // player never showed it because Media3 picks the best one itself.
        val rendition = best(manifest, requireFlac)
        require(!requireFlac || rendition != null) { "DASH manifest has no FLAC rendition" }
        val renditionScope = rendition?.body?.takeIf {
            Regex("<SegmentTemplate", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        require(!requireFlac || renditionScope != null) { "FLAC rendition has no segment template" }
        val scope = renditionScope ?: manifest
        val template = Regex("<SegmentTemplate([^>]*)>", RegexOption.IGNORE_CASE).find(scope)?.groupValues?.get(1)
            ?: error("DASH manifest has no segment template")
        fun attr(name: String) =
            Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(template)?.groupValues?.get(1)

        val initialization = attr("initialization")?.let(::unescape)
            ?: error("DASH manifest has no initialization segment")
        val mediaTemplate = attr("media")?.let(::unescape)
            ?: error("DASH manifest has no media template")
        // Only `$Number$` is supported. `$Time$` would need the timeline's
        // running offsets rather than its durations, and no module serves it.
        if (!mediaTemplate.contains("\$Number\$")) error("Unsupported DASH media template")
        val timescale = attr("timescale")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0
        val startNumber = attr("startNumber")?.toIntOrNull() ?: 1

        // A SegmentTimeline states each segment's real length, including the
        // short final one, which is what makes the EXTINF values below exact
        // rather than a division that leaves the last segment overstated.
        val ticks = mutableListOf<Long>()
        // From the chosen rendition's own template: each one ends on a
        // different final segment length.
        Regex("<SegmentTimeline>(.*?)</SegmentTimeline>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(scope)?.groupValues?.get(1)
            ?.let { timeline ->
                Regex("<S\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(timeline).forEach { entry ->
                    val body = entry.groupValues[1]
                    fun of(name: String) =
                        Regex("""\b$name\s*=\s*"([^"]*)"""").find(body)?.groupValues?.get(1)
                    val d = of("d")?.toLongOrNull()?.takeIf { it > 0 } ?: error("Invalid DASH segment duration")
                    // `r` is how many times the entry *repeats*, so r="56" is
                    // 57 segments. Reading it as a count drops one segment and
                    // truncates every download by a few seconds.
                    val repeats = of("r")?.toIntOrNull() ?: 0
                    require(repeats in 0 until MAX_SEGMENTS - ticks.size) { "Unsupported DASH repeat count" }
                    repeat(repeats + 1) { ticks += d }
                }
            }
        if (ticks.isEmpty()) {
            // No timeline: fall back to a fixed duration repeated across the
            // presentation, which is the other legal way to write this.
            val d = attr("duration")?.toLongOrNull()?.takeIf { it > 0 }
                ?: error("DASH manifest has neither a timeline nor a segment duration")
            val total = Regex("""mediaPresentationDuration\s*=\s*"([^"]*)"""").find(manifest)
                ?.groupValues?.get(1)?.let(::isoSeconds)
                ?: error("DASH manifest states no duration")
            val count = max(1, ceil(total / (d / timescale)).toInt())
            require(count <= MAX_SEGMENTS) { "DASH manifest has too many segments" }
            repeat(count) { at ->
                val remaining = total * timescale - at.toDouble() * d
                ticks += minOf(d, remaining.roundToLong()).coerceAtLeast(1)
            }
        }

        val media = ticks.indices.map { at ->
            mediaTemplate.replace("\$Number\$", (startNumber + at).toString())
        }
        if (media.isEmpty()) error("DASH manifest has no segments")
        return Plan(initialization, media, ticks.map { it / timescale })
    }

    private class Rendition(val bandwidth: Long, val codecs: String, val body: String)

    /**
     * The rendition to keep: lossless if there is one, else the highest
     * bandwidth. Null for a manifest with no `Representation` at all, which
     * leaves the template wherever the manifest put it.
     */
    private fun best(manifest: String, requireFlac: Boolean): Rendition? {
        val renditions = Regex(
            """<Representation\b([^>]*?)(?:/>|>(.*?)</Representation>)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(manifest).map { match ->
            val attrs = match.groupValues[1]
            fun attr(name: String) =
                Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
            Rendition(
                bandwidth = attr("bandwidth")?.toLongOrNull() ?: 0L,
                codecs = attr("codecs").orEmpty().lowercase(),
                body = match.groupValues[2],
            )
        }
        return renditions.filter { !requireFlac || it.codecs.startsWith("flac") }.maxWithOrNull(
            compareBy<Rendition>({ it.codecs.isLossless() }, { it.bandwidth }),
        )
    }

    private fun String.isLossless() = startsWith("flac") || startsWith("alac")

    /** Writes an HLS index for the original fMP4 segments. Index zero is the initialization segment. */
    fun playlist(plan: Plan, segmentPath: (Int) -> String): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        appendLine("#EXT-X-TARGETDURATION:${ceil(plan.seconds.maxOrNull() ?: 0.0).toInt()}")
        appendLine("""#EXT-X-MAP:URI="${segmentPath(0)}"""")
        plan.seconds.forEachIndexed { at, seconds ->
            val millis = (seconds * 1000).roundToLong()
            appendLine("#EXTINF:${millis / 1000}.${(millis % 1000).toString().padStart(3, '0')},")
            appendLine(segmentPath(at + 1))
        }
        appendLine("#EXT-X-ENDLIST")
    }

    /** The five entities an MPD's URLs can arrive escaped with — `&amp;` being the one that always does. */
    private fun unescape(value: String) = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /** `PT3M50.461S` → `230.461`. Hours, minutes and seconds, which is all a track ever needs. */
    private fun isoSeconds(value: String): Double? {
        val match = Regex("""PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?""")
            .find(value) ?: return null
        val (h, m, s) = match.destructured
        val seconds = (h.toDoubleOrNull() ?: 0.0) * 3600 +
            (m.toDoubleOrNull() ?: 0.0) * 60 +
            (s.toDoubleOrNull() ?: 0.0)
        return seconds.takeIf { it > 0 }
    }
}
