package com.music.laya.proxy

import android.content.Context
import com.music.laya.auth.EncryptedPrefs
import com.music.laya.data.AppProxyKind
import com.music.laya.data.AppProxyRoute
import java.util.UUID

/** Endpoints and the proxy user name; its password in encrypted prefs; WireGuard keys in encrypted no-backup storage. */
object AppProxyStore {
    private const val PREFS = "laya_app_proxy_v1"
    private const val PASSWORD_KEY = "password_key"
    fun configured(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains("mode")
    @Synchronized
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
                    password = prefs.getString(PASSWORD_KEY, null)?.let { key ->
                        checkNotNull(secrets(context).getString(key, null)) { "Saved proxy password unavailable" }
                    }.orEmpty(),
                ).validated()
            }
        }.getOrElse { AppProxyRoute.FAIL_CLOSED }
    }
    @Synchronized
    fun save(context: Context, route: AppProxyRoute) {
        val checked = route.validated()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousKey = prefs.getString(PASSWORD_KEY, null)
        // Commit the new secret before publishing the route that references it. A settings-write
        // failure must not make the previous endpoint read the replacement password.
        val key = checked.password.takeIf { it.isNotEmpty() }?.let { password ->
            UUID.randomUUID().toString().also {
                check(secrets(context).edit().putString(it, password).commit())
            }
        }
        check(prefs.edit()
            .putString("mode", checked.kind.name).putString("host", checked.host).putInt("port", checked.port)
            .putString("username", checked.username).putString(PASSWORD_KEY, key).commit())
        if (previousKey != null) runCatching { secrets(context).edit().remove(previousKey).commit() }
    }

    /** A proxy password: encrypted at rest, like the account and source secrets. */
    private fun secrets(context: Context) =
        EncryptedPrefs.open(context, "laya_app_proxy_secret")
}
