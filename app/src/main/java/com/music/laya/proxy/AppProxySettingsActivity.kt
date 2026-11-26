package com.music.laya.proxy

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.laya.data.AppProxyKind
import com.music.laya.data.AppProxyRoute
import com.music.laya.ui.androidText
import com.music.laya.ui.screens.SettingsGroup
import com.music.laya.ui.screens.SettingsRow
import com.music.laya.ui.screens.RowDivider
import com.music.laya.ui.theme.LayaTheme

/** The same app-local route is frozen for API, streams, downloads and Chromium. */
class AppProxySettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LayaTheme {
            val initial = remember { AppProxyStore.read(this) }
            var kind by remember { mutableStateOf(initial.kind) }
            var host by remember { mutableStateOf(initial.host) }
            var port by remember { mutableStateOf(if (initial.port == 0) "" else initial.port.toString()) }
            var username by remember { mutableStateOf(initial.username) }
            // Never shown back: empty keeps the saved one (as on iOS), and clearing the user name clears both.
            var password by remember { mutableStateOf("") }
            var error by remember { mutableStateOf(false) }
            var restart by remember { mutableStateOf(false) }
            val importer = rememberWireGuardImportUiState()
            WireGuardImportRestartDialog(importer)
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    TextButton(onClick = { finish() }) { Text(androidText("뒤로", "Back")) }
                    Text(androidText("WireGuard / 프록시", "WireGuard / proxy"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(24.dp))
                    SettingsGroup(header = androidText("연결 방식", "Connection")) {
                        AppProxyKind.entries.forEachIndexed { index, option ->
                            if (index > 0) RowDivider()
                            val label = when (option) {
                                AppProxyKind.DIRECT -> androidText("끄기 · 직접 연결", "Off · direct connection")
                                AppProxyKind.WIREGUARD_LOCAL -> "WireGuard"
                                AppProxyKind.HTTP -> "HTTP"
                                AppProxyKind.HTTPS -> "HTTPS"
                                AppProxyKind.SOCKS5 -> "SOCKS5"
                            }
                            SettingsRow(title = label, onClick = { kind = option; error = false }, trailing = {
                                RadioButton(selected = kind == option, onClick = { kind = option; error = false })
                            })
                        }
                    }
                    if (kind == AppProxyKind.WIREGUARD_LOCAL) {
                        SettingsGroup(header = "WireGuard", footer = androidText("설정 파일을 가져오면 Laya를 다시 열어 적용합니다.", "Import a profile and reopen Laya to apply it.")) {
                            SettingsRow(title = androidText(".conf 파일 가져오기", "Import .conf file"),
                                value = if (WireGuardProfileStore.hasProfile(this@AppProxySettingsActivity)) androidText("저장됨", "Saved") else null,
                                onClick = importer.openConf)
                        }
                    } else if (kind != AppProxyKind.DIRECT) {
                        Column(Modifier.padding(horizontal = 24.dp)) {
                            OutlinedTextField(host, { host = it; error = false }, label = { Text(androidText("서버 주소", "Server host")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(port, { port = it; error = false }, label = { Text(androidText("포트", "Port")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(username, { username = it; error = false },
                                label = { Text(androidText("사용자 이름 (선택)", "User name (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(password, { password = it; error = false },
                                label = { Text(androidText("비밀번호", "Password")) },
                                placeholder = { Text(if (initial.password.isNotEmpty() && username == initial.username)
                                    androidText("저장됨 (비우면 유지)", "Saved (leave empty to keep)") else androidText("선택", "Optional")) },
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                                singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    importer.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp)) }
                    if (error) Text(androidText("주소·포트를 확인하고, WireGuard는 먼저 .conf를 가져와 주세요.", "Check the endpoint; import a .conf before enabling WireGuard."), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
                    Button(onClick = {
                        val route = when (kind) {
                            AppProxyKind.DIRECT -> AppProxyRoute.DIRECT
                            AppProxyKind.WIREGUARD_LOCAL -> AppProxyRoute(kind, "127.0.0.1", 9)
                            else -> {
                                val user = username.trim()
                                val pass = when {
                                    user.isEmpty() -> ""
                                    password.isNotEmpty() -> password
                                    user == initial.username -> initial.password // left empty: keep the saved one
                                    else -> ""
                                }
                                AppProxyRoute(kind, host.trim(), port.toIntOrNull() ?: 0, username = user, password = pass)
                            }
                        }
                        if (kind == AppProxyKind.WIREGUARD_LOCAL && !WireGuardProfileStore.hasProfile(this@AppProxySettingsActivity)) error = true
                        else runCatching { route.validated(); AppProxyStore.save(this@AppProxySettingsActivity, route) }
                            .onSuccess { restart = true }.onFailure { error = true }
                    }, modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp)) { Text(androidText("저장하고 다시 열기", "Save and reopen")) }
                }
            }
            if (restart) AlertDialog(onDismissRequest = { restart = false },
                title = { Text(androidText("연결 설정 적용", "Apply connection settings")) },
                text = { Text(androidText("Laya를 다시 열면 새 연결 설정이 적용됩니다.", "Reopen Laya to apply the new connection settings.")) },
                confirmButton = { TextButton(onClick = { AppProxyRelaunchActivity.restartFrom(this@AppProxySettingsActivity) }) { Text(androidText("다시 열기", "Reopen")) } })
        } }
    }
}
