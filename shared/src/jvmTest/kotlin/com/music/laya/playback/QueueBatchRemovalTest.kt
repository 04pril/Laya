package com.music.laya.playback

import com.music.laya.data.model.Song
import kotlin.test.*

class QueueBatchRemovalTest {
    private fun song(slot: String, id: String = "same-video") = Song(id, slot, "Artist", thumbnailUrl = null, queueEntryId = slot)

    @Test fun removesOnlySelectedUpcomingOccurrences() {
        val queue = listOf(song("history"), song("playing"), song("copy-a"), song("copy-b"), song("other", "other-video"))
        val removal = QueueBatchRemoval.create(queue, 1, setOf("history", "playing", "copy-b"))!!
        assertEquals(listOf("history", "playing", "copy-a", "other"), removal.after.map { it.queueEntryId })
        assertEquals(queue, removal.before)
        assertTrue(removal.canUndo(removal.after, 1))
    }

    @Test fun undoCannotResurrectAnOldQueueAfterEditsOrTrackChanges() {
        val queue = listOf(song("playing"), song("a"), song("b"))
        val removal = QueueBatchRemoval.create(queue, 0, setOf("b"))!!
        assertFalse(removal.canUndo(removal.after + song("new"), 0))
        assertFalse(removal.canUndo(removal.after, 1))
        assertFalse(removal.canUndo(listOf(song("new-playing"), song("a")), 0))
    }

    @Test fun noUndoForNoopOrMissingCurrentTrack() {
        assertNull(QueueBatchRemoval.create(listOf(song("playing")), 0, setOf("playing")))
        assertNull(QueueBatchRemoval.create(emptyList(), -1, setOf("missing")))
    }
}
