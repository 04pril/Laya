package com.music.laya.data

import java.net.InetSocketAddress
import java.net.Proxy

/** Per-process route. */
enum class AppProxyKind { DIRECT, HTTP, HTTPS, SOCKS5, WIREGUARD_LOCAL }

data class AppProxyRoute(
    val kind: AppProxyKind = AppProxyKind.DIRECT,
    val host: String = "",
    val port: Int = 0,
    /** For an HTTP(S) or SOCKS5 server that asks for a login (#13: AdGuard VPN's SOCKS5 mode). Empty: none. */
    val username: String = "",
    val password: String = "",
) {
    val hasCredentials: Boolean get() = username.isNotEmpty()

    /** Never prints the password: routes reach logs and crash reports. */
    override fun toString() = "AppProxyRoute(kind=$kind, host=$host, port=$port, username=${if (hasCredentials) "set" else "none"})"

    fun validated(): AppProxyRoute {
        if (kind == AppProxyKind.DIRECT) {
            require(host.isEmpty() && port == 0 && !hasCredentials && password.isEmpty()) { "Direct mode cannot specify a proxy" }
            return this
        }
        // RFC 1929 carries each in at most 255 bytes; Basic auth cannot carry a ':' in the user name.
        require(username.encodeToByteArray().size <= 255 && password.encodeToByteArray().size <= 255) { "Proxy login is too long" }
        require(':' !in username && username.none { it.code < 32 || it.code == 127 }) { "Invalid proxy user name" }
        require(password.isEmpty() || hasCredentials) { "A proxy password needs a user name" }
        require(kind != AppProxyKind.WIREGUARD_LOCAL || !hasCredentials) { "WireGuard takes no proxy login" }
        require(port in 1..65535) { "Proxy port must be 1..65535" }
        require(host.isNotBlank() && host == host.trim()) { "Invalid proxy host" }
        require(host.none { it.isWhitespace() || it == '/' || it == '@' || it == '#' || it.code < 33 || it.code == 127 }) { "Enter a host, not a URL" }
        require(!host.contains("://")) { "Enter a host, not a URL" }
        if (kind == AppProxyKind.WIREGUARD_LOCAL) {
            require(host == "127.0.0.1" || host == "::1") { "WireGuard listener must use loopback" }
        }
        return this
    }

    fun javaProxy(): Proxy = when (validated().kind) {
        AppProxyKind.DIRECT -> Proxy.NO_PROXY
        AppProxyKind.HTTP, AppProxyKind.HTTPS, AppProxyKind.WIREGUARD_LOCAL ->
            Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
        AppProxyKind.SOCKS5 ->
            Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
    }

    companion object {
        val DIRECT = AppProxyRoute()
        /** Invalid saved proxy settings must never silently fall back to the Internet. */
        val FAIL_CLOSED = AppProxyRoute(AppProxyKind.HTTP, "127.0.0.1", 9)
    }
}
