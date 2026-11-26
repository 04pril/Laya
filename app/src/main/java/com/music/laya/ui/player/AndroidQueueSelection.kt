package com.music.laya.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.music.laya.data.model.Song
import com.music.laya.ui.androidText

internal object AndroidQueueActions {
    var remove: (Set<String>) -> Unit = {}
    var undo: () -> Unit = {}
    var canUndo by mutableStateOf(false)
}

@Composable
internal fun AndroidQueueSelection(queue: List<Song>, currentIndex: Int, locked: Boolean) {
    var choosing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val upcoming = queue.drop(currentIndex + 1).filter { it.queueEntryId != null }
    LaunchedEffect(upcoming) { selected = selected.intersect(upcoming.mapNotNull { it.queueEntryId }.toSet()) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        if (AndroidQueueActions.canUndo && !locked) TextButton(onClick = AndroidQueueActions.undo) { Text(androidText("삭제 되돌리기", "Undo removal")) }
        TextButton(enabled = !locked && upcoming.isNotEmpty(), onClick = { selected = emptySet(); choosing = true }) { Text(androidText("여러 곡 선택", "Select tracks")) }
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false },
        title = { Text(androidText("재생 대기 목록 선택", "Select upcoming tracks")) },
        text = { Column {
            TextButton(enabled = !locked, onClick = { selected = upcoming.mapNotNull { it.queueEntryId }.toSet() }) { Text(androidText("전체 선택", "Select all")) }
            LazyColumn { items(upcoming, key = { it.queueEntryId!! }) { song ->
                val id = song.queueEntryId!!
                Row(Modifier.fillMaxWidth().clickable(enabled = !locked) { selected = if (id in selected) selected - id else selected + id }) {
                    Checkbox(id in selected, onCheckedChange = null)
                    Column { Text(song.title); Text(song.artist, style = MaterialTheme.typography.bodySmall) }
                }
            } }
        } },
        confirmButton = { TextButton(enabled = selected.isNotEmpty() && !locked, onClick = { AndroidQueueActions.remove(selected); choosing = false }) { Text(androidText("선택 삭제", "Remove selected")) } },
        dismissButton = { TextButton(onClick = { choosing = false }) { Text(androidText("취소", "Cancel")) } })
}
