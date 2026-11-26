package com.music.laya.proxy

import android.content.Context
import com.music.laya.data.AppProxyKind
import com.music.laya.data.AppProxyRoute

/** Endpoints and the proxy user name; its password in encrypted prefs; WireGuard keys in encrypted no-backup storage. */
object AppProxyStore {
    private const val PREFS = "laya_app_proxy_v1"
    fun configured(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("mode")
    fun read(context: Context): AppProxyRoute {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return runCatching {
            val kind = AppProxyKind.valueOf(prefs.getString("mode", AppProxyKind.DIRECT.name)!!)
            when (kind) {
                AppProxyKind.DIRECT -> AppProxyRoute.DIRECT
                AppProxyKind.WIREGUARD_LOCAL -> AppProxyRoute(kind, "127.0.0.1", 9)
                else -> AppProxyRoute(
                    kind, prefs.getString("host", "").orEmpty(), prefs.getInt("port", 0),
                    username = prefs.getString("username", "").orEmpty(),
                    password = secrets(context).getString("password", "").orEmpty(),
                ).validated()
            }
        }.getOrElse { AppProxyRoute.FAIL_CLOSED }
    }
    fun save(context: Context, route: AppProxyRoute) {
        val checked = route.validated()
        // The password first and in the encrypted store; the endpoint (and user name) in the plain one, as before.
        check(secrets(context).edit().putString("password", checked.password).commit())
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", checked.kind.name).putString("host", checked.host).putInt("port", checked.port)
            .putString("username", checked.username).commit())
    }

    /** A proxy password: encrypted at rest, like the account and source secrets. */
    private fun secrets(context: Context) =
        com.music.laya.auth.EncryptedPrefs.open(context, "laya_app_proxy_secret", "laya_app_proxy_secret_plain")
}
