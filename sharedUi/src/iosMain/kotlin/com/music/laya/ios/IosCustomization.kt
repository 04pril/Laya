package com.music.laya.ios

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.laya.ui.screens.SettingsGroup
import com.music.laya.ui.screens.SettingsRow
import com.music.laya.ui.screens.RowDivider
import com.music.laya.data.settings.AppSettings
import com.music.laya.data.settings.IosCustomization as Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import platform.WebKit.WKWebsiteDataStore

/** Native capabilities supplied before the first Kotlin view or request is created. */
interface IosCustomizationNative {
    fun outputSymbol(): String
    /** Transparent SF Symbol PNG encoded as base64; drawn by Compose, never as a separate UIKit layer. */
    fun outputSymbolPng(): String?
    fun proxyConfiguredReady(): Boolean
    fun proxyReady(): Boolean
    fun showProxy()
    fun configureBrowser(store: WKWebsiteDataStore): Boolean
    fun applyLanguage(language: String)
    fun setCarScreenOn(enabled: Boolean)
}

object IosNative {
    var host: IosCustomizationNative? = null
}

@Composable
internal fun iosText(ko: String, en: String): String {
    val language by Prefs.language.collectAsState()
    return if (language == "ko") ko else en
}

@Composable
internal fun IosLanguagePicker(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(iosText("앱 언어", "App language")) },
        text = { Column {
            listOf("ko" to "한국어", "en" to "English").forEach { (tag, name) ->
                TextButton(onClick = {
                    Prefs.setLanguage(tag)
                    IosNative.host?.applyLanguage(tag)
                    onDismiss()
                }) { Text(name) }
            }
        } }, confirmButton = {})
}

@Composable
internal fun IosCustomizationScreen(bottom: androidx.compose.ui.unit.Dp) {
    @Composable fun switch(checked: Boolean, onChange: (Boolean) -> Unit) {
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
        ))
    }
    @Composable fun toggle(ko: String, en: String, key: String, flow: MutableStateFlow<Boolean>) {
        val checked by flow.collectAsState()
        val change: (Boolean) -> Unit = {
            Prefs.set(key, flow, it)
            if (key == "keep_car_screen_on") IosNative.host?.setCarScreenOn(it)
        }
        SettingsRow(title = iosText(ko, en), onClick = { change(!checked) }, trailing = { switch(checked, change) })
    }
    @Composable fun font(ko: String, en: String, sizeKey: String, size: MutableStateFlow<Int>, weightKey: String, weight: MutableStateFlow<Int>) {
        val s by size.collectAsState(); val w by weight.collectAsState()
        SettingsGroup(header = iosText(ko, en)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(iosText("크기", "Size") + " · $s", style = MaterialTheme.typography.bodyLarge)
                Slider(s.toFloat(), { Prefs.set(sizeKey, size, it.toInt()) }, valueRange = 18f..52f, steps = 33)
                Text(iosText("굵기", "Weight") + " · $w", style = MaterialTheme.typography.bodyLarge)
                Slider(w.toFloat(), { Prefs.set(weightKey, weight, ((it / 100).toInt() * 100).coerceIn(100, 900)) }, valueRange = 100f..900f, steps = 7)
            }
        }
    }
    var languagePicker by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 62.dp, bottom = bottom + 24.dp)) {
        SettingsGroup(header = iosText("앱", "App")) {
            SettingsRow(title = iosText("앱 언어", "App language"), value = iosText("한국어", "English"), onClick = { languagePicker = true })
            RowDivider()
            SettingsRow(title = iosText("WireGuard / 프록시 설정", "WireGuard / proxy settings"), onClick = { IosNative.host?.showProxy() })
        }
        SettingsGroup(header = iosText("플레이어", "Player")) {
            toggle("간단한 하단 버튼 구성", "Simple player buttons", "simple_footer", Prefs.simpleFooter)
            RowDivider()
            toggle("함께 듣기 버튼 숨기기", "Hide Listen Together button", "hide_party", Prefs.hideParty)
            RowDivider()
            toggle("비디오 버튼 숨기기", "Hide video button", "hide_video", Prefs.hideVideo)
            RowDivider()
            toggle("곡 선택 시 전체 플레이어 열기", "Open full player when choosing a song", "open_player", Prefs.openPlayer)
        }
        SettingsGroup(header = iosText("다운로드", "Downloads"), footer = iosText(
            "기존 좋아요 곡과 새로 좋아요한 곡을 다운로드합니다. 좋아요를 취소해도 파일은 유지합니다.",
            "Downloads existing and new liked songs. Unliking keeps downloaded files.")) {
            toggle("좋아요 곡 자동 다운로드", "Download liked songs", "download_likes", Prefs.downloadLikes)
        }
        SettingsGroup(header = "CarPlay") {
            toggle("연결 중 화면 켜짐 유지", "Keep iPhone awake while connected", "keep_car_screen_on", Prefs.keepCarScreenOn)
        }
        SettingsGroup(header = iosText("가사", "Lyrics")) {
            toggle("가사 출처·변경 버튼 표시", "Show lyrics source & change button", "show_lyrics_provider", Prefs.showLyricsProvider)
            RowDivider()
            val blur by AppSettings.lyricsBlur.collectAsState()
            SettingsRow(title = iosText("가사 흐림 효과", "Lyrics blur"), onClick = { AppSettings.setLyricsBlur(!blur) }, trailing = { switch(blur, AppSettings::setLyricsBlur) })
            RowDivider()
            toggle("한글·영문 폰트 개별 설정", "Separate Korean and Latin fonts", "separate_fonts", Prefs.separateFonts)
        }
        val separate by Prefs.separateFonts.collectAsState()
        if (separate) {
            font("한글", "Korean", "korean_size", Prefs.koreanSize, "korean_weight", Prefs.koreanWeight)
            font("영문 및 기타 언어", "Latin & other languages", "latin_size", Prefs.latinSize, "latin_weight", Prefs.latinWeight)
        } else font("전체 가사", "All lyrics", "font_size", Prefs.fontSize, "font_weight", Prefs.fontWeight)
        SettingsGroup(header = iosText("미리보기", "Preview")) {
            val ks by (if (separate) Prefs.koreanSize else Prefs.fontSize).collectAsState()
            val kw by (if (separate) Prefs.koreanWeight else Prefs.fontWeight).collectAsState()
            val ls by (if (separate) Prefs.latinSize else Prefs.fontSize).collectAsState()
            val lw by (if (separate) Prefs.latinWeight else Prefs.fontWeight).collectAsState()
            Column(Modifier.padding(24.dp)) {
                Text("지금 이 순간을 기억해", fontSize = ks.sp, fontWeight = FontWeight(kw), lineHeight = (ks * 1.24f).sp)
                Spacer(Modifier.height(12.dp))
                Text("Remember this moment", fontSize = ls.sp, fontWeight = FontWeight(lw), lineHeight = (ls * 1.24f).sp)
            }
        }
    }
    if (languagePicker) IosLanguagePicker { languagePicker = false }
}

private val nativeLabels = mapOf(
    "Audio Pipeline" to "오디오 처리 경로", "Track info" to "곡 정보", "Source" to "음원 출처",
    "Format" to "형식", "Bit depth" to "비트 깊이", "Sample rate" to "샘플레이트",
    "Bitrate" to "비트레이트", "Channels" to "채널", "Resampler" to "리샘플러",
    "I/O rate" to "입력/출력 샘플레이트", "Output device" to "출력 기기", "Device name" to "기기 이름",
    "Route" to "출력 경로", "I/O buffer" to "입력/출력 버퍼", "iPhone Speaker" to "iPhone 스피커",
    "Title" to "제목", "Artist" to "아티스트", "Album" to "앨범", "Release date" to "발매일",
    "Views" to "조회수", "Likes" to "좋아요", "Comments" to "댓글", "Turn off" to "끄기",
    "Mono" to "모노", "Stereo" to "스테레오",
)

@Composable
internal fun iosLabel(english: String): String {
    val language by Prefs.language.collectAsState()
    return if (language == "ko") nativeLabels[english] ?: english else english
}
