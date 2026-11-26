package com.music.laya.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioMixBrowseIdTest {
    @Test
    fun recognizesMixIdsWithAndWithoutTheBrowsePrefix() {
        assertTrue(YtMusicRepository.isRadioMix("RDAMVMexample"))
        assertTrue(YtMusicRepository.isRadioMix("VLRDAMVMexample"))
        assertTrue(YtMusicRepository.isRadioMix("VLRDCLAK5uy_example"))
    }

    @Test
    fun leavesRegularPlaylistsLikedMusicAndAlbumsPaginated() {
        assertFalse(YtMusicRepository.isRadioMix("VLPLexample"))
        assertFalse(YtMusicRepository.isRadioMix(YtMusicRepository.LIKED_MUSIC))
        assertFalse(YtMusicRepository.isRadioMix("VLOLAK5uy_example"))
    }
}
