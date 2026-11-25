package com.music.laya.ios

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.laya.data.settings.IosCustomization
import com.music.laya.ui.onboarding.OnboardingTitle
import com.music.laya.ui.onboarding.OnboardingPillButton
import com.music.laya.sharedui.resources.Res
import com.music.laya.sharedui.resources.ic_logo
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.delay
import platform.Foundation.NSUserDefaults

/** A page inside the original welcome, sharing its mesh, typography and capsules. */
@Composable
internal fun ColumnScope.IosProxyWelcome(onContinue: () -> Unit) {
    val latestContinue by rememberUpdatedState(onContinue)
    val finish = {
        NSUserDefaults.standardUserDefaults.setBool(true, "ios_proxy_welcome_done")
        latestContinue()
    }
    LaunchedEffect(Unit) {
        // Import/enable requires a restart. Once that launch actually has the
        // configured transport running, proceed without another Continue tap.
        while (true) {
            if (IosNative.host?.proxyConfiguredReady() == true) { finish(); break }
            delay(500)
        }
    }
    Spacer(Modifier.height(56.dp))
    Image(painterResource(Res.drawable.ic_logo), null, Modifier.size(23.dp, 30.dp))
    Spacer(Modifier.height(24.dp))
    OnboardingTitle(iosText("먼저 연결을 설정하세요", "Set up your connection first"))
    Spacer(Modifier.height(20.dp))
    Text(iosText("로그인 전에 앱 내부 WireGuard 또는 HTTP·HTTPS·SOCKS5 프록시를 설정할 수 있습니다.", "Configure app-local WireGuard or an HTTP, HTTPS or SOCKS5 proxy before signing in."),
        style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.78f))
    Spacer(Modifier.weight(1f))
    OnboardingPillButton(iosText("프록시 설정", "Configure proxy"), { IosNative.host?.showProxy() }, primary = true)
    Spacer(Modifier.height(12.dp))
    Text(iosText("연결 설정 후 앱을 다시 열면 이름 입력으로 자동 이동합니다. 직접 연결을 쓰려면 프록시를 끄고 계속하세요.", "Reopen after setting up the connection to go straight to your name. For a direct connection, leave the proxy off and continue."),
        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(horizontal = 4.dp))
    Spacer(Modifier.height(24.dp))
    OnboardingPillButton(iosText("계속", "Continue"), {
        if (IosNative.host?.proxyReady() != false) finish()
        else IosPlayerHost.showMessage(if (IosCustomization.language.value == "ko") "프록시 설정을 적용하려면 앱을 다시 열어 주세요." else "Reopen the app to apply the proxy settings.")
    }, primary = false)
    Row(Modifier.align(Alignment.CenterHorizontally)) {
        TextButton(onClick = { IosCustomization.setLanguage("ko"); IosNative.host?.applyLanguage("ko") }) { Text("한국어", color = Color.White) }
        TextButton(onClick = { IosCustomization.setLanguage("en"); IosNative.host?.applyLanguage("en") }) { Text("English", color = Color.White) }
    }
    Spacer(Modifier.height(16.dp))
}
