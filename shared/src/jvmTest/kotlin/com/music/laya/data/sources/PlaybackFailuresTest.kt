package com.music.laya.data.sources

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackFailuresTest {
    @Test
    fun `a failed source is ruled out for that track only`() {
        val loveAttack = TrackMatcher.Target(title = "LOVE ATTACK", artist = "RESCENE")
        PlaybackFailures.record("qobuz-config", loveAttack)
        assertEquals(setOf("qobuz-config"), PlaybackFailures.ruledOut(TrackMatcher.Target(title = "Love Attack", artist = "RESCENE")))
        assertTrue(PlaybackFailures.ruledOut(TrackMatcher.Target(title = "LOVE ATTACK", artist = "Fhin")).isEmpty())
        assertTrue(PlaybackFailures.ruledOut(TrackMatcher.Target(title = "Other", artist = "RESCENE")).isEmpty())
    }

    @Test
    fun `a soundtrack's source note does not split the track`() {
        PlaybackFailures.record("qobuz-config", TrackMatcher.Target(title = "Across the Stars (From \"Star Wars\"/Score)", artist = "John Williams"))
        assertEquals(setOf("qobuz-config"), PlaybackFailures.ruledOut(TrackMatcher.Target(title = "Across the Stars", artist = "John Williams")))
    }
}
