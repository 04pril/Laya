package com.music.laya.proxy

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.music.laya.data.AppProxyKind
import com.music.laya.data.Http
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Android WebView does not inherit OkHttp proxy settings. */
object AppProxyWebView {
    suspend fun prepare(context: Context): Boolean {
        val route = Http.configuredRoute // process-frozen route, not unsaved editor state
        val saved = AppProxyStore.read(context)
        // Importing a .conf or switching modes during onboarding updates
        // preferences but DOES NOT reconfigure OkHttp/native/WebView until
        // the app process restarts. Prevent a Google login from using the old
        // direct route before the chosen WireGuard mode has taken effect.
        if (saved.kind != route.kind) return false
        if (saved.kind != AppProxyKind.WIREGUARD_LOCAL && saved != route) return false
        if (saved.kind == AppProxyKind.WIREGUARD_LOCAL && !AppWireGuard.isReady) return false
        if (route.kind == AppProxyKind.DIRECT) return true
        // If a selected native tunnel could not start, never show the Google
        // login screen with a possibly direct or unprotected WebView route.
        if (AppWireGuard.isBlocked) return false
        return applyRoute(context, route)
    }

    /** Separate transport setup so device tests exercise the real Chromium override. */
    internal suspend fun applyRoute(context: Context, route: com.music.laya.data.AppProxyRoute): Boolean {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return false
        val scheme = when (route.kind) { AppProxyKind.SOCKS5 -> "socks"; AppProxyKind.HTTPS -> "https"; else -> "http" }
        val host = if (":" in route.host) "[${route.host}]" else route.host
        // Do not permit the standard localhost / link-local bypass to
        // silently escape an explicitly selected app-local tunnel.
        val config = ProxyConfig.Builder()
            .addProxyRule("$scheme://$host:${route.port}")
            .removeImplicitRules()
            .build()
        // A provider that never invokes the override callback cannot leave
        // the login screen waiting indefinitely; fail closed after 5 seconds.
        return withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine<Boolean> { continuation ->
            try {
                ProxyController.getInstance().setProxyOverride(
                    config,
                    ContextCompat.getMainExecutor(context),
                    Runnable { if (continuation.isActive) continuation.resume(true) },
                )
            } catch (_: Exception) {
                if (continuation.isActive) continuation.resume(false)
            }
            }
        } ?: false
    }
}
