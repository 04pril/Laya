package com.music.laya.ios

import com.music.laya.data.MonotonicClock
import com.music.laya.data.TrackLog
import com.music.laya.data.model.Song
import com.music.laya.data.settings.AppSettings
import com.music.laya.platform.ConcurrentMap
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

internal class IosPlaybackLog(private val engine: AudioEngine) {
    companion object {
        val enabled = NSBundle.mainBundle.objectForInfoDictionaryKey("LayaURLScheme") == "layatest"
    }

    private val tidalAttempts = ConcurrentMap<String, String>()
    private var progress: Progress? = null

    private class Progress(val id: String, val requestedAt: Long) {
        var started = false
        var state: String? = null
        var bufferingAt: Long? = null
    }

    fun event(id: String, message: String) {
        if (enabled) TrackLog.playbackDiagnostic(id, message)
    }

    private fun quality() =
        "quality: wifi=${AppSettings.audioQualityWifi.value.name} cellular=${AppSettings.audioQualityCellular.value.name} " +
            "metered=${AppSettings.meteredConnection.value ?: "unknown"} effective=${AppSettings.effectiveAudioQuality.name} " +
            "override=${AppSettings.streamCeilingOverride.value?.name ?: "none"}"

    fun start(id: String, at: Long, reason: String) {
        if (!enabled) return
        progress = Progress(id, MonotonicClock.nowMs())
        event(id, "$reason positionMs=$at")
        event(id, quality())
    }

    fun source(id: String, name: String, url: String, mime: String, action: String) {
        if (!enabled) return
        val label = label(name)
        val tidalHls = label == "Tidal" && url.startsWith("http://127.0.0.1:") && url.contains("/tidal/")
        if (tidalHls) {
            if (tidalAttempts.size >= 32 && id !in tidalAttempts) tidalAttempts.clear()
            tidalAttempts[id] = url
        }
        event(id, "$action source=$label format=$mime transport=${if (tidalHls) "DASH-to-HLS" else "file"}")
        event(id, quality())
    }

    fun progress(id: String, positionMs: Long, durationMs: Long, playing: Boolean, buffering: Boolean) {
        if (!enabled) return
        val now = MonotonicClock.nowMs()
        val track = progress?.takeIf { it.id == id } ?: Progress(id, now).also { progress = it }
        val state = when {
            buffering -> "buffering"
            playing -> "playing"
            else -> "paused"
        }
        if (state == track.state) return
        track.state = state
        if (buffering) {
            track.bufferingAt = now
            event(id, "${if (track.started) "rebuffer" else "startup_buffer"} begin positionMs=$positionMs")
            return
        }
        track.bufferingAt?.let { start ->
            event(id, "buffer end elapsedMs=${now - start} nextState=$state positionMs=$positionMs")
            track.bufferingAt = null
        }
        if (playing && !track.started) {
            track.started = true
            event(id, "first_play elapsedMs=${now - track.requestedAt} positionMs=$positionMs durationMs=$durationMs")
        } else {
            event(id, "$state positionMs=$positionMs")
        }
    }

    fun report(song: Song): String = buildString {
        val bundle = NSBundle.mainBundle
        val version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString")
        val build = bundle.objectForInfoDictionaryKey("CFBundleVersion")
        appendLine("Laya playback diagnostic · $version ($build)")
        appendLine("device=${UIDevice.currentDevice.model} iOS=${UIDevice.currentDevice.systemVersion}")
        appendLine("track: ${song.title.lineSequence().joinToString(" ")} / ${song.artist.lineSequence().joinToString(" ")}")
        appendLine(quality())
        appendLine("settings: crossfadeSeconds=${AppSettings.crossfadeSeconds.value} " +
            "smartFade=${AppSettings.smartFadeEnabled.value} skipSilence=${AppSettings.skipSilence.value}")
        val route = engine.outputRoute()
        appendLine("output: type=${route.getOrNull(1)} rateHz=${route.getOrNull(2)} " +
            "channels=${route.getOrNull(3)} bufferMs=${route.getOrNull(4)}")
        engine.playbackDiagnostics(tidalAttempts[song.videoId]).forEach(::appendLine)
        appendLine()
        appendLine("Playback timeline")
        val timeline = TrackLog.playbackTimeline(song.videoId)
        if (timeline.isEmpty()) appendLine("No events for this track in the current app session.")
        timeline.forEach(::appendLine)
    }

    fun label(name: String) = when (name) {
        "Tidal", "Qobuz", "YouTube", "Downloaded", "JioSaavn" -> name
        else -> "Other"
    }
}
