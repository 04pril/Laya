package com.music.laya.proxy

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.music.laya.data.AppProxyRoute
import com.music.laya.data.settings.IosCustomization
import com.music.laya.ui.androidText
import com.music.laya.ui.onboarding.OnboardingBrand
import com.music.laya.ui.onboarding.OnboardingPillButton
import com.music.laya.ui.onboarding.OnboardingTitle

@Composable
fun ColumnScope.ProxyWelcome(onContinue: () -> Unit) {
    val context = LocalContext.current
    val importer = rememberWireGuardImportUiState()
    WireGuardImportRestartDialog(importer)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
            OnboardingBrand()
            Spacer(Modifier.height(24.dp))
            OnboardingTitle(androidText("먼저 연결을 설정하세요", "Set up your connection"))
            Spacer(Modifier.height(12.dp))
            Text(
                androidText(
                    "로그인 전에 WireGuard 설정 파일을 가져오거나 프록시를 설정할 수 있습니다.",
                    "Import a WireGuard profile or configure a proxy before signing in.",
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))
            OnboardingPillButton(
                label = androidText("WireGuard .conf 가져오기", "Import WireGuard .conf"),
                onClick = importer.openConf,
                primary = true,
                enabled = !importer.importing,
            )
            importer.error?.let { Text(it, color = Color.White, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(12.dp))
            OnboardingPillButton(
                label = androidText("HTTP · HTTPS · SOCKS5 프록시 설정", "HTTP · HTTPS · SOCKS5 proxy"),
                onClick = { context.startActivity(Intent(context, AppProxySettingsActivity::class.java)) },
                primary = false,
            )
            Spacer(Modifier.height(12.dp))
            OnboardingPillButton(
                label = androidText("직접 연결로 계속", "Continue with direct connection"),
                onClick = {
                    AppProxyStore.save(context, AppProxyRoute.DIRECT)
                    onContinue()
                },
                primary = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                for ((tag, title) in listOf("ko" to "한국어", "en" to "English")) {
                    TextButton(onClick = {
                        IosCustomization.setLanguage(tag)
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                    }) { Text(title, color = Color.White) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
