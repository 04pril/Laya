package com.music.laya.proxy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.music.laya.ui.androidText

@Composable
fun ColumnScope.ProxyWelcome(onContinue: () -> Unit) {
    val context = LocalContext.current
    val importer = rememberWireGuardImportUiState()
    WireGuardImportRestartDialog(importer)
    Text(androidText("먼저 연결을 설정하세요", "Set up your connection"), fontSize = 34.sp,
        fontWeight = FontWeight.Bold, color = Color.White)
    Spacer(Modifier.weight(1f))
    Text(androidText("로그인 전에 WireGuard 설정 파일을 가져오거나 프록시를 설정할 수 있습니다.",
        "Import a WireGuard profile or configure a proxy before signing in."), color = Color.White)
    Spacer(Modifier.height(24.dp))
    Button(onClick = importer.openConf, enabled = !importer.importing,
        modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
        Text(androidText("WireGuard .conf 가져오기", "Import WireGuard .conf"))
    }
    importer.error?.let { Text(it, color = Color.White, modifier = Modifier.padding(top = 8.dp)) }
    TextButton(onClick = { context.startActivity(android.content.Intent(context, AppProxySettingsActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) {
        Text(androidText("HTTP · HTTPS · SOCKS5 프록시 설정", "HTTP · HTTPS · SOCKS5 proxy settings"), color = Color.White)
    }
    Spacer(Modifier.height(16.dp))
    Button(onClick = { AppProxyStore.save(context, com.music.laya.data.AppProxyRoute.DIRECT); onContinue() },
        modifier = Modifier.fillMaxWidth().height(60.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
        Text(androidText("직접 연결로 계속", "Continue with direct connection"))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for ((tag, title) in listOf("ko" to "한국어", "en" to "English")) {
            TextButton(onClick = {
                com.music.laya.data.settings.IosCustomization.setLanguage(tag)
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(tag))
            }) { Text(title, color = Color.White) }
        }
    }
    Spacer(Modifier.height(8.dp))
}
