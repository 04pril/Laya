package com.music.laya.ios

import com.music.laya.data.YtMusicRepository
import com.music.laya.data.model.Song
import com.music.laya.data.settings.AppSettings
import com.music.laya.data.settings.IosCustomization
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Same policy as our BitChord fork: sync the liked collection, then enqueue new successful likes. */
internal object IosLikedDownloads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pending = linkedMapOf<String, Song>()
    private var installed = false
    fun install() {
        if (installed) return
        installed = true
        scope.launch {
            combine(IosCustomization.downloadLikes, IosAuth.signedIn, IosAuth.sessionVersion) { enabled, signedIn, version ->
                if (enabled && signedIn) version else null
            }.collectLatest { session ->
                pending.clear()
                if (session == null) return@collectLatest
                val songs = YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC).getOrNull() ?: return@collectLatest
                ensureActive()
                if (IosAuth.sessionVersion.value != session) return@collectLatest
                songs.filter { it.localUri == null }.forEach { pending[it.videoId] = it }
                drain()
            }
        }
        scope.launch {
            AppSettings.meteredConnection.collect { drain() }
        }
        scope.launch { AppSettings.wifiOnlyDownloads.collect { drain() } }
    }
    fun liked(song: Song) {
        if (!IosCustomization.downloadLikes.value || !IosAuth.signedIn.value || song.localUri != null) return
        pending[song.videoId] = song
        drain()
    }
    private fun drain() {
        if (!IosCustomization.downloadLikes.value || !IosAuth.signedIn.value || !AppSettings.downloadsAllowedNow) return
        val songs = pending.values.toList(); pending.clear()
        songs.forEach(IosDownloads::enqueue)
    }
}
