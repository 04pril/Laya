@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.music.laya.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.laya.data.NerdStats
import com.music.laya.data.SongInfo
import com.music.laya.data.SongInfoRepository
import com.music.laya.data.TrackLog
import com.music.laya.data.YtMusicRepository
import com.music.laya.data.compactCount
import com.music.laya.data.model.Song
import com.music.laya.data.model.UserPlaylist
import com.music.laya.platform.currentTimeMillis
import kotlinx.coroutines.launch
import platform.Foundation.NSFileManager
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.UIKit.UIPasteboard

/** Android's "Copy log": the same TrackLog export (header, NerdStats, the track's resolver/source/player lines) on the pasteboard. Returns the line count. */
internal suspend fun copyTrackLog(song: Song): Int {
    val text = TrackLog.forTrack(song, NerdStats.current.value)
    UIPasteboard.generalPasteboard.string = text
    return text.lines().count()
}

internal fun sharePlaybackLog(text: String): Boolean {
    val path = NSTemporaryDirectory() + "Laya-playback-${currentTimeMillis()}.txt"
    val bytes = text.encodeToByteArray()
    if (bytes.isEmpty()) return false
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    if (!data.writeToFile(path, true)) return false
    val cleanup: () -> Unit = { NSFileManager.defaultManager.removeItemAtPath(path, null) }
    return presentShare(listOf(NSURL.fileURLWithPath(path)), onComplete = cleanup).also {
        if (!it) cleanup()
    }
}

@Composable
private fun PickRow(label: String, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(label) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.clickable(onClick = onClick),
)

/** Sleep timer picker with Android's presets (15/30/45/60) plus "end of track" and off. */
@Composable
internal fun IosSleepTimerDialog(player: IosPlayer, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(iosText("취침 타이머", "Sleep timer")) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { m ->
                    PickRow(iosText("${m}분", "$m minutes") + if (player.sleepMinutes == m) "  ✓" else "") { player.startSleep(m); onDismiss() }
                }
                PickRow(iosText("이 곡이 끝나면", "End of track") + if (player.sleepAfterTrack) "  ✓" else "") { player.startSleepAfterTrack(); onDismiss() }
                if (player.sleepMinutes != null || player.sleepAfterTrack) PickRow(iosText("끄기", "Turn off")) { player.cancelSleep(); onDismiss() }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(iosText("완료", "Done")) } },
    )
}

/** Same lookup as Android's SongInfoDialog (SongInfoRepository), same five rows. */
@Composable
internal fun IosSongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val info by produceState<SongInfo?>(null, song) { value = SongInfoRepository.load(song) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(iosText("곡 정보", "Song info")) },
        text = {
            Column {
                Text(song.title)
                Text(song.artist)
                if (info == null) Text(iosText("불러오는 중…", "Loading…"))
                else listOf(
                    "Album" to (info?.album ?: song.albumName),
                    "Release date" to (info?.releaseDate ?: info?.releaseYear),
                    "Views" to info?.views?.let(::compactCount),
                    "Likes" to info?.likes,
                    "Comments" to info?.comments,
                ).forEach { (k, v) ->
                    ListItem(
                        headlineContent = { Text(iosLabel(k)) },
                        trailingContent = { Text(v ?: "—") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(iosText("완료", "Done")) } },
    )
}

/** Lists the account's playlists (the call Android's picker makes) and adds [song] to the tapped one. */
@Composable
internal fun IosPlaylistPicker(song: Song, onDismiss: () -> Unit, notify: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val playlists by produceState<List<UserPlaylist>?>(null) { value = YtMusicRepository.userPlaylists().getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(iosText("플레이리스트에 추가", "Add to playlist")) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.material3.OutlinedTextField(query, { query = it }, label = { Text(iosText("플레이리스트 검색", "Search playlists")) }, singleLine = true)
                val list = playlists?.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
                if (list == null) Text(iosText("불러오는 중…", "Loading…")) else if (list.isEmpty()) Text(iosText("검색 결과 없음", "No playlists"))
                list?.forEach { p ->
                    PickRow(p.title) {
                        onDismiss()
                        scope.launch {
                            YtMusicRepository.addToPlaylist(p.playlistId, listOf(song.videoId))
                                .onSuccess { notify(if (com.music.laya.data.settings.IosCustomization.language.value == "ko") "${p.title}에 추가했습니다" else "Added to ${p.title}") }
                                .onFailure { notify(if (com.music.laya.data.settings.IosCustomization.language.value == "ko") "${p.title}에 추가하지 못했습니다" else "Couldn't add to ${p.title}") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text(iosText("취소", "Cancel")) } },
    )
}
