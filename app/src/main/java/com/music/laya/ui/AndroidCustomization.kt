package com.music.laya.ui

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
import kotlin.math.roundToInt

@Composable
internal fun androidText(ko: String, en: String): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return if (locale.language == "ko") ko else en
}

@Composable
internal fun AndroidCustomizationSettings(contentPadding: PaddingValues) {
    val pendingFontValues = remember { mutableStateMapOf<String, Int>() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val storagePermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) Prefs.set("download_likes", Prefs.downloadLikes, true) }

    @Composable fun switch(checked: Boolean, onChange: (Boolean) -> Unit) {
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedBorderColor = MaterialTheme.colorScheme.primary,
        ))
    }
    @Composable fun toggle(ko: String, en: String, key: String, flow: MutableStateFlow<Boolean>) {
        val checked by flow.collectAsState()
        val change: (Boolean) -> Unit = { enabled ->
            if (key == "download_likes" && enabled && com.music.laya.download.DownloadStore.needsLegacyPermission() &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else Prefs.set(key, flow, enabled)
        }
        SettingsRow(title = androidText(ko, en), onClick = { change(!checked) }, trailing = { switch(checked, change) })
    }
    @Composable fun fontValue(key: String, preference: MutableStateFlow<Int>): Int {
        val saved by preference.collectAsState()
        return pendingFontValues[key] ?: saved
    }
    @Composable fun fontSlider(label: String, key: String, preference: MutableStateFlow<Int>, range: IntRange, step: Int) {
        val value = fontValue(key, preference)
        Text("$label · $value", style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value.toFloat(),
            onValueChange = { pendingFontValues[key] = (it / step).roundToInt() * step },
            onValueChangeFinished = {
                pendingFontValues[key]?.let { Prefs.set(key, preference, it) }
                pendingFontValues.remove(key)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
    @Composable fun font(ko: String, en: String, sizeKey: String, size: MutableStateFlow<Int>, weightKey: String, weight: MutableStateFlow<Int>) {
        SettingsGroup(header = androidText(ko, en)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                fontSlider(androidText("크기", "Size"), sizeKey, size, 18..52, step = 1)
                fontSlider(androidText("굵기", "Weight"), weightKey, weight, 100..900, step = 100)
            }
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(contentPadding).padding(bottom = 24.dp)) {
        SettingsGroup(header = androidText("플레이어", "Player")) {
            toggle("함께 듣기 버튼 숨기기", "Hide Listen Together button", "hide_party", Prefs.hideParty)
            RowDivider()
            toggle("비디오 버튼 숨기기", "Hide video button", "hide_video", Prefs.hideVideo)
            RowDivider()
            toggle("곡 선택 시 전체 플레이어 열기", "Open full player when choosing a song", "open_player", Prefs.openPlayer)
        }
        SettingsGroup(header = androidText("다운로드", "Downloads"), footer = androidText(
            "기존 좋아요 곡과 새로 좋아요한 곡을 다운로드합니다. 좋아요를 취소해도 파일은 유지합니다.",
            "Downloads existing and new liked songs. Unliking keeps downloaded files.")) {
            toggle("좋아요 곡 자동 다운로드", "Download liked songs", "download_likes", Prefs.downloadLikes)
        }
        SettingsGroup(header = "Android Auto") {
            toggle("연결 중 화면 켜짐 유지", "Keep screen awake while connected", "keep_car_screen_on", Prefs.keepCarScreenOn)
        }
        SettingsGroup(header = androidText("가사", "Lyrics")) {
            toggle("가사 출처·변경 버튼 표시", "Show lyrics source & change button", "show_lyrics_provider", Prefs.showLyricsProvider)
            RowDivider()
            val blur by AppSettings.lyricsBlur.collectAsState()
            SettingsRow(title = androidText("가사 흐림 효과", "Lyrics blur"), onClick = { AppSettings.setLyricsBlur(!blur) }, trailing = { switch(blur, AppSettings::setLyricsBlur) })
            RowDivider()
            toggle("한글·영문 폰트 개별 설정", "Separate Korean and Latin fonts", "separate_fonts", Prefs.separateFonts)
        }
        val separate by Prefs.separateFonts.collectAsState()
        if (separate) {
            font("한글", "Korean", "korean_size", Prefs.koreanSize, "korean_weight", Prefs.koreanWeight)
            font("영문 및 기타 언어", "Latin & other languages", "latin_size", Prefs.latinSize, "latin_weight", Prefs.latinWeight)
        } else font("전체 가사", "All lyrics", "font_size", Prefs.fontSize, "font_weight", Prefs.fontWeight)
        SettingsGroup(header = androidText("미리보기", "Preview")) {
            val ks = if (separate) fontValue("korean_size", Prefs.koreanSize) else fontValue("font_size", Prefs.fontSize)
            val kw = if (separate) fontValue("korean_weight", Prefs.koreanWeight) else fontValue("font_weight", Prefs.fontWeight)
            val ls = if (separate) fontValue("latin_size", Prefs.latinSize) else ks
            val lw = if (separate) fontValue("latin_weight", Prefs.latinWeight) else kw
            Column(Modifier.padding(24.dp)) {
                Text("지금 이 순간을 기억해", fontSize = ks.sp, fontWeight = FontWeight(kw), lineHeight = (ks * 1.24f).sp)
                Spacer(Modifier.height(12.dp))
                Text("Remember this moment", fontSize = ls.sp, fontWeight = FontWeight(lw), lineHeight = (ls * 1.24f).sp)
            }
        }
    }
}
