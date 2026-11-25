package com.music.laya.ios

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.music.laya.data.YtMusicRepository
import com.music.laya.data.model.Song
import com.music.laya.data.model.SongMenu
import kotlinx.coroutines.launch

/** Feedback tokens and playlist ownership are looked up for the active account, never inferred from likes. */
@Composable
internal fun IosRemovalActions(song: Song, onChanged: () -> Unit, onDismissMenu: () -> Unit) {
    val session by IosAuth.sessionVersion.collectAsState()
    val signedIn by IosAuth.signedIn.collectAsState()
    if (!signedIn || song.localUri != null) return
    val scope = rememberCoroutineScope()
    var menu by remember(song.videoId, session) { mutableStateOf<SongMenu?>(null) }
    var playlist by remember(song.queueEntryId, session) { mutableStateOf<Pair<String, String>?>(null) }
    var confirmation by remember(song.videoId, session) { mutableStateOf<String?>(null) }
    var busy by remember(song.videoId, session) { mutableStateOf(false) }
    LaunchedEffect(song.videoId, song.setVideoId, song.playbackSourceId, session) {
        val version = session
        menu = YtMusicRepository.songMenu(song.videoId).getOrNull()
        val browse = song.playbackSourceId?.takeIf { it.startsWith("VL") }
        if (browse != null) {
            val page = YtMusicRepository.browseSongs(browse).getOrNull()
            if (page?.owned == true && IosAuth.sessionVersion.value == version) {
                // The playing entry may be beyond the first page. Its original slot is authoritative;
                // only fall back to a lookup when there is exactly one matching occurrence.
                val slot = song.setVideoId ?: page.songs.filter { it.videoId == song.videoId }.singleOrNull()?.setVideoId
                slot?.let { playlist = browse.removePrefix("VL") to it }
            }
        }
    }
    if (menu?.inLibrary == true && menu?.removeFromLibraryToken != null) {
        TextButton(enabled = !busy, onClick = { confirmation = "library" }) { Text(iosText("보관함에서 삭제", "Remove from library")) }
    }
    if (playlist != null) TextButton(enabled = !busy, onClick = { confirmation = "playlist" }) { Text(iosText("이 플레이리스트에서 삭제", "Remove from this playlist")) }
    confirmation?.let { kind ->
        AlertDialog(onDismissRequest = { if (!busy) confirmation = null },
            title = { Text(if (kind == "library") iosText("보관함에서 삭제", "Remove from library") else iosText("이 플레이리스트에서 삭제", "Remove from this playlist")) },
            text = { Text(song.title) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                val version = session
                val token = menu?.removeFromLibraryToken
                val target = playlist
                busy = true
                scope.launch {
                    if (!IosAuth.signedIn.value || IosAuth.sessionVersion.value != version) { busy = false; confirmation = null; return@launch }
                    val result = if (kind == "library" && token != null) YtMusicRepository.setLibraryStatus(token)
                        else if (target != null) YtMusicRepository.removeFromPlaylist(target.first, listOf(target.second to song.videoId))
                        else Result.failure(IllegalStateException("Missing playlist entry"))
                    busy = false
                    confirmation = null
                    if (IosAuth.sessionVersion.value == version) {
                        if (result.isSuccess) { onChanged(); onDismissMenu() }
                        else IosPlayerHost.showMessage(if (com.music.laya.data.settings.IosCustomization.language.value == "ko") "삭제하지 못했습니다. 다시 시도해 주세요." else "Couldn't remove this track. Try again.")
                    }
                }
            }) { Text(iosText("삭제", "Remove")) } },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmation = null }) { Text(iosText("취소", "Cancel")) } })
    }
}
