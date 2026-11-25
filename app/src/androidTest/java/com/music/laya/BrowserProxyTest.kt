package com.music.laya

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.music.laya.data.AppProxyKind
import com.music.laya.data.AppProxyRoute
import com.music.laya.proxy.AppProxyWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BrowserProxyTest {
    @Test fun chromiumConnectReachesTheAppConfiguredHttpProxy() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue("Provider does not expose Chromium proxy override",
            androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE))
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        var browser: WebView? = null
        try {
            val request = executor.submit<String> {
                server.accept().use { socket ->
                    socket.soTimeout = 6000
                    val input = socket.getInputStream().bufferedReader()
                    val first = input.readLine()
                    while (input.readLine()?.isNotEmpty() == true) Unit
                    socket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    first
                }
            }
            val ready = withContext(Dispatchers.Main) {
                AppProxyWebView.applyRoute(context, AppProxyRoute(AppProxyKind.HTTP,"127.0.0.1",server.localPort))
            }
            assertTrue("WebView provider must actually accept the proxy override", ready)
            instrumentation.runOnMainSync {
                browser = WebView(context).apply { loadUrl("https://laya-proxy-verification.invalid/browser") }
            }
            assertEquals("CONNECT laya-proxy-verification.invalid:443 HTTP/1.1",request.get(8,TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { browser?.destroy() }
            withContext(Dispatchers.Main) {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                    androidx.webkit.ProxyController.getInstance().clearProxyOverride(context.mainExecutor, {})
                }
            }
            server.close();executor.shutdownNow()
        }
    }
}
