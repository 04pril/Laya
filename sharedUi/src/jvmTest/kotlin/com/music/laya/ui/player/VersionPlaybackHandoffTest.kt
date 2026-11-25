package com.music.laya.ui.player

import kotlinx.coroutines.*
import kotlin.test.*

class VersionPlaybackHandoffTest {
    private class Engine(var readyId: String? = null) {
        val events = mutableListOf<String>()
        fun play(id: String) { readyId = id; events += "play:$id" }
        fun swap(from: String, to: String) {
            // AVQueuePlayer's source swap is refused while not ready; it cannot start an item.
            if (readyId == from) { readyId = to; events += "swap:$to" }
        }
    }

    @Test fun firstVideoCanSwitchToAudioBeforeItsOriginalResolveFinishes() = runBlocking {
        for (priorItem in listOf(null, "previous-song")) {
            val engine = Engine(priorItem)
            val originalReady = CompletableDeferred<Unit>()
            val pending = launch(start = CoroutineStart.UNDISPATCHED) {
                originalReady.await()
                engine.play("video")
            }
            handoffPlayerVersion("video", priorItem, true, { pending.cancel() }) { mode ->
                if (mode == VersionPlaybackMode.START) engine.play("audio") else engine.swap("video", "audio")
            }
            originalReady.complete(Unit)
            pending.join()
            assertTrue(pending.isCancelled)
            assertEquals("audio", engine.readyId)
            assertEquals(listOf("play:audio"), engine.events, "A late video result must not replace the selected audio")
        }
    }

    @Test fun handedButBufferingVideoIsStartedFresh() {
        val engine = Engine()
        var cancelled = false
        handoffPlayerVersion("video", "video", true, { cancelled = true }) { mode ->
            assertTrue(cancelled, "Cancel the original request before submitting its replacement")
            if (mode == VersionPlaybackMode.START) engine.play("audio") else engine.swap("video", "audio")
        }
        assertEquals(listOf("play:audio"), engine.events)
    }

    @Test fun readyCurrentVideoKeepsTheSeamlessSwapPath() {
        val engine = Engine("video")
        handoffPlayerVersion("video", "video", false, {}) { mode ->
            if (mode == VersionPlaybackMode.START) engine.play("audio") else engine.swap("video", "audio")
        }
        assertEquals(listOf("swap:audio"), engine.events)
    }

    @Test fun previousReadyItemCannotBeMistakenForTheSelectedVideo() {
        val engine = Engine("previous-song")
        handoffPlayerVersion("video", "previous-song", false, {}) { mode ->
            if (mode == VersionPlaybackMode.START) engine.play("audio") else engine.swap("video", "audio")
        }
        assertEquals(listOf("play:audio"), engine.events)
    }
}
