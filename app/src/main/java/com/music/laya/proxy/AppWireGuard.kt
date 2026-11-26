package com.music.laya.proxy

import android.content.Context
import android.util.Base64
import app.morphe.extension.shared.wireguard.NativeTunnel
import com.music.laya.data.AppProxyKind
import com.music.laya.data.AppProxyRoute
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Process-latched userspace WireGuard. The native source is built into the APK,
 * not run through Morphe patches or the Android system VPN slot.
 *
 * Native ready means only that an authenticated 127.0.0.1 HTTP CONNECT proxy
 * is listening; it does NOT prove the remote peer has completed a handshake.
 */
object AppWireGuard {
    private const val USER = "laya"
    private const val REALM = "App WireGuard"
    private const val LOCAL = "127.0.0.1"
    private val failureMessage = (
        "HTTP/1.1 503 Service Unavailable\r\n" +
        "Connection: close\r\nContent-Length: 0\r\n\r\n"
    ).toByteArray(StandardCharsets.US_ASCII)

    data class Startup(val route: AppProxyRoute, val proxyAuthorization: String? = null)
    private data class Credentials(val username: String, val password: String)

    @Volatile private var credentials: Credentials? = null
    @Volatile private var started = false
    @Volatile private var blocked = false
    private var failureListener: ServerSocket? = null
    private val wasInitialized = AtomicBoolean(false)

    val isReady: Boolean get() = started
    val isBlocked: Boolean get() = blocked

    /** Exactly once, before the first shared OkHttp client or login WebView. */
    @Synchronized
    fun startAtProcessLaunch(context: Context, selection: AppProxyRoute): Startup {
        check(wasInitialized.compareAndSet(false, true)) { "WireGuard startup is process-latched" }
        if (selection.kind != AppProxyKind.WIREGUARD_LOCAL) return Startup(selection)
        return try {
            val profile = WireGuardProfileStore.load(context)
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            val password = try {
                Base64.encodeToString(
                    randomBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                )
            } finally {
                randomBytes.fill(0)
            }
            NativeTunnel.validate(profile)
            val port = NativeTunnel.start(profile, USER, password, 0)
            check(port in 1..65535) { "WireGuard returned no listener port" }
            val token = Base64.encodeToString(
                "$USER:$password".toByteArray(StandardCharsets.US_ASCII),
                Base64.NO_WRAP,
            )
            credentials = Credentials(USER, password)
            started = true
            Startup(
                route = AppProxyRoute(AppProxyKind.WIREGUARD_LOCAL, LOCAL, port),
                proxyAuthorization = "Basic $token",
            )
        } catch (_: Exception) {
            // No direct-network fallback. Never log native error messages or keys.
            blocked = true
            blockedRoute()
        } catch (_: LinkageError) {
            // Missing/unsupported .so must not make protected requests go direct.
            blocked = true
            blockedRoute()
        }
    }

    /**
     * Android WebView runs a separate Chromium stack. A proxy-auth challenge
     * is answered ONLY for our own loopback proxy and its unique realm.
     * Never reuse these credentials for accounts.google.com or another host.
     *
     * Note: WebView provider support for HTTP proxy 407 remains device-dependent.
     * If it never exposes this challenge, it must fail closed, not bypass.
     */
    fun credentialForWebView(host: String?, realm: String?): Pair<String, String>? {
        val c = credentials ?: return null
        if (!started || realm != REALM || host != LOCAL) return null
        return c.username to c.password
    }

    private fun blockedRoute(): Startup {
        val listener = try {
            ServerSocket().apply {
                reuseAddress = false
                bind(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0), 16)
            }
        } catch (_: IOException) {
            return Startup(AppProxyRoute.FAIL_CLOSED)
        }
        failureListener = listener
        thread(name = "laya-wireguard-blocked", isDaemon = true) {
            while (!listener.isClosed) {
                val socket: Socket = try {
                    listener.accept()
                } catch (_: IOException) {
                    break
                }
                runCatching {
                    socket.use {
                        it.soTimeout = 5000
                        it.getOutputStream().write(failureMessage)
                    }
                }
            }
        }
        return Startup(AppProxyRoute(AppProxyKind.HTTP, LOCAL, listener.localPort))
    }
}
