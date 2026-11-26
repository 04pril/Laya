package com.music.laya.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** #8: detail pages stop at the first page for generated mixes only. */
class EndlessMixTest {
    @Test
    fun `generated mixes are endless`() {
        assertTrue(YtMusicRepository.isEndlessMix("VLRDTMAK5uy_kset8DisdE7LSXrIk"))
        assertTrue(YtMusicRepository.isEndlessMix("RDAMVMdQw4w9WgXcQ"))
    }

    @Test
    fun `ordinary playlists, liked music and albums are followed to the end`() {
        assertFalse(YtMusicRepository.isEndlessMix("VLPLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))
        assertFalse(YtMusicRepository.isEndlessMix("VLLM"))
        assertFalse(YtMusicRepository.isEndlessMix("VLOLAK5uy_abc"))
        assertFalse(YtMusicRepository.isEndlessMix("MPREb_abc"))
    }
}
