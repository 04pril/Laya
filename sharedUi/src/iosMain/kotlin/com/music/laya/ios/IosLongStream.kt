package com.music.laya.ios

import com.music.laya.data.MonotonicClock
import com.music.laya.data.TrackLog
import com.music.laya.data.innertube.SidxIndex
import com.music.laya.platform.NetHttp
import io.ktor.http.Url

/**
 * A long YouTube stream (15 minutes and over) handed to AVPlayer as HLS over byte ranges of the same file, served by
 * the loopback HLS server Tidal already uses (#6). Opened directly, AVPlayer read nearly all of a 443 minute mix before
 * it played: 424 MB, 46 s on Wi-Fi, almost five minutes over a VPN.
 *
 * Only long streams: an HLS item carries no audio tap, so EQ, karaoke, crossfade and skip-silence do not apply to it
 * (as for Tidal today). A 15 minute plus YouTube upload is a mix or a set, with no lossless copy or lyrics to lose,
 * and an ordinary track already starts in about a second.
 */
internal object IosLongStream {
    /** Set at launch: registers a playlist with the loopback server, segments fetched with the given headers. */
    var register: ((playlist: String, segments: List<String>, headers: Map<String, String>) -> String?)? = null

    private const val MIN_SECONDS = 15 * 60
    private const val READ_TIMEOUT_MS = 8_000L

    /** A loopback HLS URL for [url] when it is a long YouTube audio stream whose index can be read; otherwise null. */
    fun hlsFor(videoId: String, url: String, headers: Map<String, String>, mime: String): String? {
        if (!mime.startsWith("audio/mp4") || IosCast.active) return null // Cast receivers can't reach loopback
        val seconds = runCatching { Url(url).parameters["dur"]?.toDoubleOrNull() }.getOrNull() ?: return null
        if (seconds < MIN_SECONDS) return null
        val register = register ?: return null
        val started = MonotonicClock.nowMs()
        var want = SidxIndex.FIRST_READ
        repeat(3) {
            val (status, head) = runCatching {
                NetHttp.readHead(url, headers + ("Range" to "bytes=0-${want - 1}"), want, READ_TIMEOUT_MS)
            }.getOrElse {
                TrackLog.w("Laya", "long stream: reading the index failed (${it.message}); playing the file directly", about = videoId)
                return null
            }
            if (status !in 200..299) {
                TrackLog.w("Laya", "long stream: index read answered HTTP $status; playing the file directly", about = videoId)
                return null
            }
            when (val parsed = SidxIndex.parse(head)) {
                is SidxIndex.Result.Found -> {
                    val index = parsed.index
                    val playlist = SidxIndex.playlist(index) { "segment-$it.m4s" }
                    val hls = register(playlist, List(index.segments.size + 1) { url }, headers)
                    TrackLog.d(
                        "Laya",
                        "long stream (${(seconds / 60).toInt()} min) as HLS: ${index.segments.size} segments, " +
                            "index read ${head.size / 1024} KB in ${MonotonicClock.nowMs() - started} ms" +
                            if (hls == null) "; the loopback server refused it, playing the file directly" else "",
                        about = videoId,
                    )
                    return hls
                }
                is SidxIndex.Result.NeedMore -> want = parsed.bytes
                SidxIndex.Result.Unusable -> {
                    TrackLog.d("Laya", "long stream: no segment index at the head; playing the file directly", about = videoId)
                    return null
                }
            }
        }
        return null
    }

    /** Whether [url] is one of these loopback playlists (or Tidal's): no file behind it for karaoke or analysis. */
    fun isLoopback(url: String) = url.startsWith("http://127.0.0.1:") && url.endsWith("/playlist.m3u8")
}
