package com.music.laya.data.innertube

import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * The segment index (`sidx`) at the head of a YouTube DASH audio file, turned into an HLS playlist over byte ranges
 * of that same file.
 *
 * This lets the iOS player request individual fragments during playback instead of opening the full MP4 (#6).
 *
 * The layout YouTube serves: `ftyp`, `moov` (the initialization segment), `sidx`, then `moof`/`mdat` pairs, which the
 * `sidx` references by size and duration from the first byte after itself.
 */
object SidxIndex {
    class Segment(val offset: Long, val size: Long, val seconds: Double)

    /** [initLength] bytes from 0 are the initialization segment (`ftyp` + `moov`). */
    class Index(val initLength: Long, val segments: List<Segment>)

    sealed interface Result {
        class Found(val index: Index) : Result
        /** The `sidx` runs past what was read: read again up to [bytes]. */
        class NeedMore(val bytes: Long) : Result
        /** Not a file laid out this way; play it the old way. */
        object Unusable : Result
    }

    /** Reads [head], the first bytes of the file. */
    fun parse(head: ByteArray): Result {
        var at = 0L
        var initEnd = -1L
        while (at + 8 <= head.size) {
            var size = u32(head, at)
            val type = head.decodeToString(at.toInt() + 4, at.toInt() + 8)
            var header = 8L
            if (size == 1L) {
                if (at + 16 > head.size) return Result.NeedMore(at + 16)
                size = u64(head, at + 8)
                header = 16
            }
            if (size < header || size > MAX_HEAD - at) return Result.Unusable
            when (type) {
                "ftyp", "moov", "free", "styp" -> if (type == "moov") initEnd = at + size
                "sidx" -> {
                    if (initEnd <= 0) return Result.Unusable
                    if (at + size > head.size) return Result.NeedMore(at + size)
                    return sidx(head, at + header, at + size, initEnd)?.let(Result::Found) ?: Result.Unusable
                }
                else -> return Result.Unusable // media (moof/mdat) before any index
            }
            at += size
        }
        return if (at > 0 && at + 8 <= MAX_HEAD) Result.NeedMore(minOf(at + 8 + 64 * 1024, MAX_HEAD)) else Result.Unusable
    }

    private fun sidx(b: ByteArray, start: Long, end: Long, initEnd: Long): Index? {
        var p = start
        fun need(n: Long) = p + n <= end
        if (!need(4)) return null
        val version = b[p.toInt()].toInt() and 0xFF
        p += 4 // version + flags
        if (!need(8)) return null
        p += 4 // reference_ID
        val timescale = u32(b, p).takeIf { it > 0 } ?: return null
        p += 4
        val firstOffset: Long
        if (version == 0) {
            if (!need(8)) return null
            p += 4 // earliest_presentation_time
            firstOffset = u32(b, p); p += 4
        } else if (version == 1) {
            if (!need(16)) return null
            p += 8
            firstOffset = u64(b, p); p += 8
        } else return null
        if (!need(4)) return null
        p += 2 // reserved
        val count = ((b[p.toInt()].toInt() and 0xFF) shl 8) or (b[p.toInt() + 1].toInt() and 0xFF)
        p += 2
        if (count == 0 || !need(count * 12L)) return null
        if (firstOffset < 0 || firstOffset > Long.MAX_VALUE - end) return null
        var offset = end + firstOffset // anchored at the first byte after the sidx box
        val segments = ArrayList<Segment>(count)
        repeat(count) {
            val word = u32(b, p)
            if (word and 0x80000000L != 0L) return null // references another sidx: not a layout seen here
            val size = word and 0x7FFFFFFFL
            val duration = u32(b, p + 4)
            p += 12
            if (size <= 0 || duration <= 0 || size > Long.MAX_VALUE - offset) return null
            segments += Segment(offset, size, duration.toDouble() / timescale)
            offset += size
        }
        return Index(initEnd, segments)
    }

    /**
     * An HLS VOD playlist over byte ranges of one file: [path] names the URI of segment `i` (0 is the
     * initialization map, then the media segments from 1), all of them the same file on the server side.
     */
    fun playlist(index: Index, path: (Int) -> String): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:7")
        appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        appendLine("#EXT-X-TARGETDURATION:${ceil(index.segments.maxOf { it.seconds }).toInt().coerceAtLeast(1)}")
        appendLine("""#EXT-X-MAP:URI="${path(0)}",BYTERANGE="${index.initLength}@0"""")
        index.segments.forEachIndexed { at, s ->
            val millis = (s.seconds * 1000).roundToLong()
            appendLine("#EXTINF:${millis / 1000}.${(millis % 1000).toString().padStart(3, '0')},")
            appendLine("#EXT-X-BYTERANGE:${s.size}@${s.offset}")
            appendLine(path(at + 1))
        }
        appendLine("#EXT-X-ENDLIST")
    }

    /** How much of the head to read first: room for `ftyp`, `moov` and a long mix's `sidx` (12 bytes a fragment). */
    const val FIRST_READ = 256L * 1024
    private const val MAX_HEAD = 4L * 1024 * 1024

    private fun u32(b: ByteArray, at: Long): Long {
        val i = at.toInt()
        return ((b[i].toLong() and 0xFF) shl 24) or ((b[i + 1].toLong() and 0xFF) shl 16) or
            ((b[i + 2].toLong() and 0xFF) shl 8) or (b[i + 3].toLong() and 0xFF)
    }

    private fun u64(b: ByteArray, at: Long): Long = (u32(b, at) shl 32) or u32(b, at + 4)
}
