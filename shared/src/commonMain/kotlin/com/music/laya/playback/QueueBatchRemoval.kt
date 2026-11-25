package com.music.laya.playback

import com.music.laya.data.model.Song

/** An undo is valid only for the exact queue and current entry it was created for. */
class QueueBatchRemoval private constructor(val before: List<Song>, val after: List<Song>, val currentId: String?) {
    fun canUndo(queue: List<Song>, currentIndex: Int): Boolean =
        queue == after && queue.getOrNull(currentIndex)?.queueEntryId == currentId

    companion object {
        fun create(queue: List<Song>, currentIndex: Int, entryIds: Set<String>): QueueBatchRemoval? {
            if (currentIndex !in queue.indices) return null
            val after = queue.filterIndexed { i, song -> i <= currentIndex || song.queueEntryId !in entryIds }
            if (after == queue) return null
            return QueueBatchRemoval(queue.toList(), after, queue[currentIndex].queueEntryId)
        }
    }
}
