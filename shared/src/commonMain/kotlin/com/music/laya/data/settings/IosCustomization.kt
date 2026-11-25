package com.music.laya.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/** Opt-in iOS preferences. Other platform hosts retain their current appearance. */
object IosCustomization {
    var installed = false; private set
    private var store: Settings? = null
    val language = MutableStateFlow("en")
    val hideParty = MutableStateFlow(false)
    val hideVideo = MutableStateFlow(false)
    val openPlayer = MutableStateFlow(true)
    val downloadLikes = MutableStateFlow(false)
    val keepCarScreenOn = MutableStateFlow(false)
    val showLyricsProvider = MutableStateFlow(true)
    val separateFonts = MutableStateFlow(false)
    val customFontSizeKeys = MutableStateFlow<Set<String>>(emptySet())
    val fontSize = MutableStateFlow(34)
    val fontWeight = MutableStateFlow(800)
    val koreanSize = MutableStateFlow(34)
    val koreanWeight = MutableStateFlow(800)
    val latinSize = MutableStateFlow(34)
    val latinWeight = MutableStateFlow(800)

    fun install(settings: Settings, defaultLanguage: String, defaultFontSize: Int = 34) {
        store = settings
        installed = true
        language.value = settings.getString("app_language_override", defaultLanguage).let { if (it == "ko") "ko" else "en" }
        listOf("hide_party" to hideParty, "hide_video" to hideVideo,
            "open_player" to openPlayer, "download_likes" to downloadLikes, "keep_car_screen_on" to keepCarScreenOn,
            "separate_fonts" to separateFonts).forEach { (key, flow) ->
            flow.value = settings.getBoolean("ios_$key", key in setOf("hide_party", "hide_video", "open_player"))
        }
        // Preserve an older decision to hide either part when merging the two switches.
        showLyricsProvider.value = settings.getBoolean("ios_show_lyrics_provider",
            settings.getBoolean("ios_show_lyrics_source", true) && settings.getBoolean("ios_show_lyrics_change", true))
        settings.putBoolean("ios_show_lyrics_provider", showLyricsProvider.value)
        customFontSizeKeys.value = setOf("font_size", "korean_size", "latin_size").filter { settings.hasKey("ios_$it") }.toSet()
        listOf("font_size" to fontSize, "font_weight" to fontWeight, "korean_size" to koreanSize,
            "korean_weight" to koreanWeight, "latin_size" to latinSize, "latin_weight" to latinWeight).forEach { (key, flow) ->
            val range = if (key.endsWith("size")) 18..52 else 100..900
            flow.value = settings.getInt("ios_$key", if (key.endsWith("size")) defaultFontSize else 800).coerceIn(range)
        }
    }

    fun set(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) { store?.putBoolean("ios_$key", value); flow.value = value }
    fun set(key: String, flow: MutableStateFlow<Int>, value: Int) {
        store?.putInt("ios_$key", value)
        if (key in setOf("font_size", "korean_size", "latin_size")) customFontSizeKeys.value = customFontSizeKeys.value + key
        flow.value = value
    }
    fun setLanguage(value: String) { if (value in setOf("ko", "en")) { store?.putString("app_language_override", value); language.value = value } }
}
