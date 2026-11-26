package com.music.laya.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.music.laya.data.YtMusicRepository
import com.music.laya.data.model.Song
import com.music.laya.ui.androidText
import kotlinx.coroutines.launch

/** An owned playlist and its exact entry are resolved for the current account. */
@Composable
fun PlayingSongRemoval(song: Song, identity: String, identityNow: () -> String, onChanged: () -> Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var slot by remember(song.queueEntryId,identity) { mutableStateOf<Pair<String,String>?>(null) }
    var confirmation by remember(song.queueEntryId,identity) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(song.queueEntryId,song.setVideoId,song.playbackSourceId,identity) {
        val id=song.playbackSourceId?.takeIf { it.startsWith("VL") } ?: return@LaunchedEffect
        val page=YtMusicRepository.browseSongs(id).getOrNull() ?: return@LaunchedEffect
        if (page.owned == true && identityNow()==identity) {
            val entry=song.setVideoId ?: page.songs.filter { it.videoId==song.videoId }.singleOrNull()?.setVideoId
            if(entry!=null) slot=id.removePrefix("VL") to entry
        }
    }
    if(slot!=null) TextButton(onClick={confirmation=true}) { Text(androidText("이 플레이리스트에서 삭제","Remove from this playlist")) }
    if(confirmation) AlertDialog(onDismissRequest={if(!busy) confirmation=false},
        title={Text(androidText("이 플레이리스트에서 삭제","Remove from this playlist"))},text={Text(song.title)},
        confirmButton={TextButton(enabled=!busy,onClick={
            val target=slot ?: return@TextButton
            busy=true
            scope.launch {
                if(identityNow()!=identity) { busy=false;confirmation=false;return@launch }
                val result=YtMusicRepository.removeFromPlaylist(target.first,listOf(target.second to song.videoId))
                busy=false;confirmation=false
                if(identityNow()==identity) {
                    if(result.isSuccess) onChanged()
                    else android.widget.Toast.makeText(context,if(context.resources.configuration.locales[0].language=="ko") "삭제하지 못했습니다." else "Couldn't remove track.",android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }){Text(androidText("삭제","Remove"))}},
        dismissButton={TextButton(enabled=!busy,onClick={confirmation=false}){Text(androidText("취소","Cancel"))}})
}
