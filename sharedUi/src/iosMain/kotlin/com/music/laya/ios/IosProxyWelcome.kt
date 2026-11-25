package com.music.laya.ios

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.laya.data.settings.IosCustomization
import platform.Foundation.NSUserDefaults

/** A proxy-first step, before the original name and account onboarding. */
@Composable
internal fun IosProxyWelcome(onContinue: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Text(iosText("먼저 연결을 설정하세요", "Set up your connection first"), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(18.dp))
            Text(iosText("로그인 전에 앱 내부 WireGuard 또는 HTTP·HTTPS·SOCKS5 프록시를 설정할 수 있습니다.", "Configure app-local WireGuard or an HTTP, HTTPS or SOCKS5 proxy before signing in."))
            Spacer(Modifier.height(18.dp))
            Button(onClick = { IosNative.host?.showProxy() }, modifier = Modifier.fillMaxWidth()) { Text(iosText("프록시 설정", "Configure proxy")) }
            Text(iosText("연결 설정을 바꿨다면 앱을 완전히 종료하고 다시 여세요. 직접 연결을 쓰려면 프록시를 끄고 계속하세요.", "After changing the connection, close and reopen the app. To connect directly, leave the proxy off and continue."), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                if (IosNative.host?.proxyReady() != false) {
                    NSUserDefaults.standardUserDefaults.setBool(true, "ios_proxy_welcome_done")
                    onContinue()
                } else IosPlayerHost.showMessage(if (IosCustomization.language.value == "ko") "프록시 설정을 적용하려면 앱을 다시 열어 주세요." else "Reopen the app to apply the proxy settings.")
            }, modifier = Modifier.fillMaxWidth()) { Text(iosText("계속", "Continue")) }
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                TextButton(onClick = { IosCustomization.setLanguage("ko"); IosNative.host?.applyLanguage("ko") }) { Text("한국어") }
                TextButton(onClick = { IosCustomization.setLanguage("en"); IosNative.host?.applyLanguage("en") }) { Text("English") }
            }
        }
    }
}
