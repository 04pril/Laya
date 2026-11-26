package com.music.laya.data.innertube

import com.music.laya.platform.HttpRequest
import com.music.laya.platform.NetHttp
import com.music.laya.platform.currentTimeMillis

// ponytail: no NewPipe on iOS; InnerTubeX is the only resolver there
internal actual object StreamPlatform {
    actual fun init() {}

    actual val newPipeAvailable = false

    // ponytail: AAC only until Phase 8 adds an Opus/WebM decoder.
    actual val playbackRequiresM4a = true

    actual fun isLinkageError(e: Throwable) = false

    /**
     * The player script's signature timestamp, which PlaybackTracker's history registration can't be answered without
     * (NewPipe supplies it on the JVM). The current player id comes from iframe_api; the figure is read out of its
     * base.js, as yt-dlp does. StreamResolver caches it, so this runs once.
     */
    actual suspend fun signatureTimestamp(videoId: String): Int? {
        // Asked every few seconds while it fails (PlaybackTracker retries per tick): don't refetch a 3 MB script each time.
        if (currentTimeMillis() - stsFailedAt < 5 * 60_000L) return null
        return runCatching { fetchSignatureTimestamp() }.onFailure { stsFailedAt = currentTimeMillis() }.getOrThrow()
    }

    private var stsFailedAt = 0L

    private fun fetchSignatureTimestamp(): Int {
        val api = NetHttp.execute(HttpRequest("https://www.youtube.com/iframe_api", callTimeoutMs = 10_000L))
        val id = Regex("""player\\?/([0-9a-fA-F]{8})\\?/""").find(api.body.decodeToString())?.groupValues?.get(1)
            ?: error("no player id in iframe_api (HTTP ${api.code})")
        val js = NetHttp.execute(HttpRequest("https://www.youtube.com/s/player/$id/player_ias.vflset/en_US/base.js", callTimeoutMs = 20_000L))
        return Regex("""(?:signatureTimestamp|sts)\s*:\s*(\d{5})""").find(js.body.decodeToString())?.groupValues?.get(1)?.toIntOrNull()
            ?: error("no signatureTimestamp in player $id (HTTP ${js.code})")
    }

    actual fun permanentReason(e: Throwable): String? = null

    actual suspend fun extractStream(
        videoId: String,
        select: (List<Pair<Int, NewPipeAudio>>) -> NewPipeAudio?,
    ): StreamResolver.Stream = error("NewPipe is not available on iOS")

    // Asks for the full range the player will (so a refusal of large ranges still shows), but reads only [readBytes]
    // of it, as OkHttp does on Android: the rest of the ~1 MiB used to download, and be thrown away, before every start.
    actual fun <T> probeFetch(url: String, headers: Map<String, String>, readBytes: Long, onReply: (Int, String?, () -> Boolean) -> T): T {
        val (code, contentType, arrived) = NetHttp.probe(url, headers, readBytes, timeoutMs = 6_000L)
        return onReply(code, contentType) { arrived }
    }
}
