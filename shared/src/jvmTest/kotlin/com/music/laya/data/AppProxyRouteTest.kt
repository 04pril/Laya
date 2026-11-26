package com.music.laya.data

import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppProxyRouteTest {
    @Test fun supportsNetworkRoutes() {
        assertEquals(Proxy.Type.DIRECT, AppProxyRoute.DIRECT.javaProxy().type())
        assertEquals(Proxy.Type.HTTP, AppProxyRoute(AppProxyKind.HTTP, "proxy.example", 8080).javaProxy().type())
        assertEquals(Proxy.Type.HTTP, AppProxyRoute(AppProxyKind.HTTPS, "proxy.example", 443).javaProxy().type())
        assertEquals(Proxy.Type.SOCKS, AppProxyRoute(AppProxyKind.SOCKS5, "proxy.example", 1080).javaProxy().type())
        assertEquals(Proxy.Type.HTTP, AppProxyRoute(AppProxyKind.WIREGUARD_LOCAL, "127.0.0.1", 8080).javaProxy().type())
    }

    @Test fun rejectsInvalidEndpoints() {
        for (route in listOf(
            AppProxyRoute(AppProxyKind.HTTP, "https://proxy.example", 443),
            AppProxyRoute(AppProxyKind.HTTP, "proxy.example/path", 443),
            AppProxyRoute(AppProxyKind.HTTP, "proxy.example", 0),
            AppProxyRoute(AppProxyKind.SOCKS5, "bad host", 1080),
            AppProxyRoute(AppProxyKind.WIREGUARD_LOCAL, "203.0.113.1", 1080),
        )) assertFailsWith<IllegalArgumentException> { route.validated() }
    }

    @Test fun corruptSavedRouteFailsClosed() {
        assertEquals("127.0.0.1", AppProxyRoute.FAIL_CLOSED.host)
        assertEquals(Proxy.Type.HTTP, AppProxyRoute.FAIL_CLOSED.javaProxy().type())
    }

    @Test fun proxyLoginIsValidatedAndNeverPrinted() {
        val socks = AppProxyRoute(AppProxyKind.SOCKS5, "proxy.example", 1080, username = "user", password = "s3cret").validated()
        assertTrue(socks.hasCredentials)
        assertFalse("s3cret" in socks.toString())
        for (route in listOf(
            AppProxyRoute(AppProxyKind.SOCKS5, "proxy.example", 1080, password = "no user"),
            AppProxyRoute(AppProxyKind.HTTP, "proxy.example", 8080, username = "a:b", password = "x"),
            AppProxyRoute(AppProxyKind.WIREGUARD_LOCAL, "127.0.0.1", 8080, username = "u"),
            AppProxyRoute(username = "u"),
            AppProxyRoute(password = "no user"),
        )) assertFailsWith<IllegalArgumentException> { route.validated() }
    }

    @Test fun socksLoginAnswersOnlyThatProxyAndProtocol() {
        val route = AppProxyRoute(AppProxyKind.SOCKS5, "proxy.example", 1080, username = "user", password = "s3cret")
        val login = Http.socksLogin(route)
        fun ask(host: String, protocol: String, port: Int = 1080) = login.requestPasswordAuthenticationInstance(
            host, null, port, protocol, "SOCKS authentication", null, null, java.net.Authenticator.RequestorType.SERVER)
        assertEquals("user", ask("proxy.example", "SOCKS5")?.userName)
        assertEquals("s3cret", ask("PROXY.example", "SOCKS5")?.password?.concatToString())
        assertNull(ask("other.example", "SOCKS5"))
        assertNull(ask("proxy.example", "http"))
        assertNull(ask("proxy.example", "SOCKS5", port = 1081))
    }
}
