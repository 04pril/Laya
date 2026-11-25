package com.music.laya.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/** Opt-in iOS preferences. Other platform hosts retain their current appearance. */
object IosCustomization {
    var installed = false; private set
    private var store: Settings? = null
    val language = MutableStateFlow("en")
    val simpleFooter = MutableStateFlow(false)
    val hideParty = MutableStateFlow(false)
    val hideVideo = MutableStateFlow(false)
    val openPlayer = MutableStateFlow(true)
    val downloadLikes = MutableStateFlow(false)
    val keepCarScreenOn = MutableStateFlow(false)
    val showLyricsSource = MutableStateFlow(true)
    val showLyricsChange = MutableStateFlow(true)
    val separateFonts = MutableStateFlow(false)
    val fontSize = MutableStateFlow(34)
    val fontWeight = MutableStateFlow(800)
    val koreanSize = MutableStateFlow(34)
    val koreanWeight = MutableStateFlow(800)
    val latinSize = MutableStateFlow(34)
    val latinWeight = MutableStateFlow(800)

    fun install(settings: Settings, defaultLanguage: String) {
        store = settings
        installed = true
        language.value = settings.getString("app_language_override", defaultLanguage).let { if (it == "ko") "ko" else "en" }
        listOf("simple_footer" to simpleFooter, "hide_party" to hideParty, "hide_video" to hideVideo,
            "open_player" to openPlayer, "download_likes" to downloadLikes, "keep_car_screen_on" to keepCarScreenOn,
            "show_lyrics_source" to showLyricsSource, "show_lyrics_change" to showLyricsChange,
            "separate_fonts" to separateFonts).forEach { (key, flow) ->
            flow.value = settings.getBoolean("ios_$key", key in setOf("simple_footer", "hide_party", "hide_video", "open_player", "show_lyrics_source", "show_lyrics_change"))
        }
        listOf("font_size" to fontSize, "font_weight" to fontWeight, "korean_size" to koreanSize,
            "korean_weight" to koreanWeight, "latin_size" to latinSize, "latin_weight" to latinWeight).forEach { (key, flow) ->
            val range = if (key.endsWith("size")) 18..52 else 100..900
            flow.value = settings.getInt("ios_$key", if (key.endsWith("size")) 34 else 800).coerceIn(range)
        }
    }

    fun set(key: String, flow: MutableStateFlow<Boolean>, value: Boolean) { store?.putBoolean("ios_$key", value); flow.value = value }
    fun set(key: String, flow: MutableStateFlow<Int>, value: Int) { store?.putInt("ios_$key", value); flow.value = value }
    fun setLanguage(value: String) { if (value in setOf("ko", "en")) { store?.putString("app_language_override", value); language.value = value } }
}
