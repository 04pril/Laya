package com.music.laya.download

import android.content.Context
import android.net.Uri
import com.music.laya.data.Http
import com.music.laya.data.sources.lossless.DashAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File

/**
 * An offline copy of an MPEG-DASH audio manifest, saved as an HLS package.
 *
 * ### Why DASH exists here at all
 *
 * Because the shape of a module's stream is the backend's choice and not ours.
 * The Tidal module served `.m3u8` until September 2026 and `.mpd` afterwards —
 * same track, same requested tier, no version change on our side. Playback
 * survived that by being told the mime type
 * ([PlaybackService][com.music.laya.playback.PlaybackService]'s
 * `withResolvedStreamType`); downloads did not, because
 * [Downloads.routeFor][Downloads] recognised only `.m3u8` as a manifest and
 * treated everything else as a file. A `.mpd` therefore went down the ordinary
 * fetch-and-save path and wrote **2,738 bytes of XML into a file called
 * `Artist - Track.flac`** — a download that reported success, carried no
 * lyrics and no cover, and could never play. [MediaTagger.embed] wraps its
 * taggers in `runCatching`, so even tagging that file failed quietly.
 *
 * ### Why it is written out as HLS
 *
 * The samples are identical. Both manifests describe the same fMP4 segments —
 * an initialisation segment and a run of numbered media segments — and Media3
 * reads those through the same extractor either way. So the only real
 * difference is the index sitting in front of them, and rewriting the index is
 * far less work than teaching the rest of the app a second package format:
 * [DownloadStore.delete] finds a package by looking for a file named
 * `playlist.m3u8`, [Downloads.remember] finds the saved cover by the same
 * test, and playback picks the HLS source off the `.m3u8` extension. Emitting
 * a playlist those three already understand means none of them change, and an
 * offline DASH download is byte-for-byte as playable as an offline HLS one.
 *
 * The sibling of [OfflineHls], deliberately not merged with it. That one
 * rewrites a playlist it was given and is the code path every existing offline
 * download was saved by; this one builds a playlist from a different index
 * format. Sharing a body between them would put the proven path at risk to
 * save about twenty lines.
 *
 * ### What is not supported
 *
 * Encrypted manifests (`ContentProtection`), and multi-period ones. Both are
 * rejected outright rather than half-saved, on the same reasoning as
 * [OfflineHls]: a package that is missing part of its audio is worse than a
 * download that says it could not be made, because only the second is
 * recoverable by the person it happens to.
 */
internal object OfflineDash {

    /** Whether [url] is a manifest this can save, judged the way the app judges every other one — on the extension. */
    fun handles(url: String): Boolean =
        url.substringBefore('?').endsWith(".mpd", ignoreCase = true)

    suspend fun save(
        context: Context,
        id: String,
        url: String,
        headers: Map<String, String>,
        onProgress: (Long, Long) -> Unit,
        lyrics: LyricsTag.Embeddable?,
        artwork: MediaTagger.Artwork?,
    ): Uri = withContext(Dispatchers.IO) {
        // The same directory the HLS packages live in, and the same layout, so
        // that everything which reads one reads both. The name is historical
        // and is left alone on purpose: it is a path already stored in the
        // download records of every device that has one.
        val root = File(File(context.filesDir, "offline-hls"), id.hashCode().toUInt().toString(16))
        root.deleteRecursively()
        if (!root.mkdirs()) error("Could not create offline package")
        try {
            fun request(target: String) = Request.Builder().url(target).apply {
                headers.forEach { (name, value) -> header(name, value) }
            }.build()
            val manifest = Http.client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) error("DASH manifest failed (HTTP ${response.code})")
                response.body.string()
            }
            val plan = parse(manifest)
            val base = url.toHttpUrlOrNull() ?: error("Invalid DASH URL")

            // The initialisation segment is segment-00000 so the numbering
            // matches what OfflineHls produces for the same stream — the
            // `#EXT-X-MAP` fragment is index 0 there too.
            val remotes = listOf(plan.initialization) + plan.media
            remotes.forEachIndexed { index, remote ->
                coroutineContext.ensureActive()
                val target = base.resolve(remote)?.toString() ?: error("Invalid DASH segment")
                Http.client.newCall(request(target)).execute().use { response ->
                    if (!response.isSuccessful) error("DASH segment failed (HTTP ${response.code})")
                    val body = response.body.byteStream()
                    body.use { input -> File(root, localName(index)).outputStream().use(input::copyTo) }
                }
                onProgress((index + 1).toLong(), remotes.size.toLong())
            }

            File(root, "playlist.m3u8").writeText(playlist(plan))
            lyrics?.let { File(root, "lyrics.lrc").writeText(it.enhanced ?: it.plain.orEmpty()) }
            artwork?.let { File(root, "cover.${if (it.mime.contains("png")) "png" else "jpg"}").writeBytes(it.bytes) }
            Uri.fromFile(File(root, "playlist.m3u8"))
        } catch (e: Throwable) {
            root.deleteRecursively()
            throw e
        }
    }

    private fun localName(index: Int) = "segment-${index.toString().padStart(5, '0')}.m4s"

    internal fun parse(manifest: String): DashAudio.Plan = DashAudio.parse(manifest)

    internal fun playlist(plan: DashAudio.Plan): String = DashAudio.playlist(plan, ::localName)
}
