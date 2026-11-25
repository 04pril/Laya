package com.music.laya.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.laya.data.model.Song

@Composable
internal fun IosQueueSelection(queue: List<Song>, currentIndex: Int, locked: Boolean) {
    val player = IosPlayer.instance ?: return
    var choosing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val upcoming = queue.drop(currentIndex + 1).filter { it.queueEntryId != null }
    LaunchedEffect(upcoming) { selected = selected.intersect(upcoming.mapNotNull { it.queueEntryId }.toSet()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (player.canUndoRemoval && !locked) TextButton(onClick = player::undoRemoval) { Text(iosText("삭제 되돌리기", "Undo removal")) }
        TextButton(enabled = !locked && upcoming.isNotEmpty(), onClick = { selected = emptySet(); choosing = true }) { Text(iosText("여러 곡 선택", "Select tracks")) }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(iosText("재생 대기 목록 선택", "Select upcoming tracks")) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            TextButton(enabled = !locked, onClick = { selected = upcoming.mapNotNull { it.queueEntryId }.toSet() }) { Text(iosText("전체 선택", "Select all")) }
            upcoming.forEach { song ->
                val id = song.queueEntryId!!
                Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { selected = if (id in selected) selected - id else selected + id }.padding(vertical = 6.dp)) {
                    Checkbox(id in selected, onCheckedChange = null)
                    Column { Text(song.title); Text(song.artist, style = MaterialTheme.typography.bodySmall) }
                }
            }
        } },
        confirmButton = { TextButton(enabled = selected.isNotEmpty() && !locked, onClick = { player.removeUpcoming(selected); choosing = false }) { Text(iosText("선택 삭제", "Remove selected")) } },
        dismissButton = { TextButton(onClick = { choosing = false }) { Text(iosText("취소", "Cancel")) } })
}
