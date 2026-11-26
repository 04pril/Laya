package com.music.laya.download

import android.content.Context
import com.music.laya.data.YtMusicRepository
import com.music.laya.data.model.Song
import com.music.laya.data.settings.AppSettings
import com.music.laya.data.settings.IosCustomization
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One account-scoped synchronizer. Network restrictions hold pending entries until allowed. */
internal class LikedDownloads(private val context: Context, private val signedIn: StateFlow<Boolean>, private val identity: () -> String?) {
    private lateinit var worker: CoroutineScope
    private val pending = linkedMapOf<String, Song>()
    fun install(scope: CoroutineScope, session: Flow<String?>) {
        worker = scope
        scope.launch {
            combine(IosCustomization.downloadLikes, signedIn, session) { enabled, loggedIn, id -> if (enabled && loggedIn) id else null }
                .collectLatest { id ->
                    pending.clear()
                    if (id == null) return@collectLatest
                    val songs = YtMusicRepository.allSongs(YtMusicRepository.LIKED_MUSIC).getOrNull() ?: return@collectLatest
                    ensureActive()
                    if (identity() != id) return@collectLatest
                    songs.filter { it.localUri == null }.forEach { pending[it.videoId] = it }
                    drain()
                }
        }
        scope.launch { AppSettings.meteredConnection.collect { drain() } }
        scope.launch { AppSettings.wifiOnlyDownloads.collect { drain() } }
    }
    fun liked(song: Song) {
        if (IosCustomization.downloadLikes.value && signedIn.value && song.localUri == null) { pending[song.videoId] = song; drain() }
    }
    private fun drain() {
        if (!IosCustomization.downloadLikes.value || !signedIn.value || !AppSettings.downloadsAllowedNow) return
        val songs = pending.values.toList(); pending.clear()
        val session = identity()
        worker.launch {
            for ((index, song) in songs.withIndex()) {
                if (identity() != session || !signedIn.value || !IosCustomization.downloadLikes.value) return@launch
                if (!AppSettings.downloadsAllowedNow) {
                    songs.drop(index).forEach { pending[it.videoId] = it }
                    return@launch
                }
                val missing = Downloads.savedUri(context, song.videoId) == null
                if (identity() != session || !signedIn.value || !IosCustomization.downloadLikes.value) return@launch
                if (missing) Downloads.enqueue(context, song)
            }
        }
    }
}
