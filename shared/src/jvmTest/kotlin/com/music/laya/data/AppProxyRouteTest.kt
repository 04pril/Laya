package com.music.laya.data

import java.net.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
}
