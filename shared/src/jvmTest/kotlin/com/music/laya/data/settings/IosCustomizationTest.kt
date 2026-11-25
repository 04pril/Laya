package com.music.laya.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.*

class IosCustomizationTest {
    @Test fun newInstallAndSavedChoicesSurviveReinstallation() {
        val store = MapSettings()
        IosCustomization.install(store, "ko")
        assertEquals("ko", IosCustomization.language.value)
        assertTrue(IosCustomization.simpleFooter.value)
        assertTrue(IosCustomization.hideParty.value)
        assertTrue(IosCustomization.hideVideo.value)
        assertFalse(IosCustomization.downloadLikes.value)
        IosCustomization.set("hide_video", IosCustomization.hideVideo, false)
        IosCustomization.setLanguage("en")
        IosCustomization.install(store, "ko")
        assertEquals("en", IosCustomization.language.value)
        assertFalse(IosCustomization.hideVideo.value)
        IosCustomization.setLanguage("unsupported")
        assertEquals("en", IosCustomization.language.value)
    }

    @Test fun invalidRestoredFontsStayInSupportedRangesAndDoNotLeakAcrossStores() {
        val store = MapSettings()
        store.putInt("ios_korean_size", -100)
        store.putInt("ios_font_weight", 9999)
        IosCustomization.install(store, "en")
        assertEquals(18, IosCustomization.koreanSize.value)
        assertEquals(900, IosCustomization.fontWeight.value)
        IosCustomization.install(MapSettings(), "en")
        assertEquals(34, IosCustomization.koreanSize.value)
        assertEquals(800, IosCustomization.fontWeight.value)
    }
    @Test fun mergedProviderPreferencePreservesHiddenChoicesAndNewSelection() {
        for (source in listOf(false, true)) for (change in listOf(false, true)) {
            val store = MapSettings()
            store.putBoolean("ios_show_lyrics_source", source)
            store.putBoolean("ios_show_lyrics_change", change)
            IosCustomization.install(store, "ko")
            assertEquals(source && change, IosCustomization.showLyricsProvider.value)
            IosCustomization.set("show_lyrics_provider", IosCustomization.showLyricsProvider, true)
            IosCustomization.install(store, "ko")
            assertTrue(IosCustomization.showLyricsProvider.value)
            IosCustomization.set("show_lyrics_provider", IosCustomization.showLyricsProvider, false)
            IosCustomization.install(store, "ko")
            assertFalse(IosCustomization.showLyricsProvider.value)
        }
        IosCustomization.install(MapSettings(), "en")
        assertTrue(IosCustomization.showLyricsProvider.value)
    }

}
