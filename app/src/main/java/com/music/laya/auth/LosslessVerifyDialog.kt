package com.music.laya.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.HttpAuthHandler
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.music.laya.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.music.laya.data.sources.lossless.LosslessProvider
import com.music.laya.data.sources.lossless.ZarzSession
import com.music.laya.proxy.AppProxyWebView
import com.music.laya.proxy.AppWireGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The broker's one-time check for [provider], in a WebView. The page ends by redirecting to
 * [ZarzSession.CALLBACK] with a grant, which is caught here before Android tries to open it.
 */
@Composable
fun LosslessVerifyDialog(provider: LosslessProvider, onDone: () -> Unit) {
    var attempt by remember(provider) { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        key(provider, attempt) {
            LosslessVerificationAttempt(provider, onDone, onRestart = { attempt++ })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LosslessVerificationAttempt(provider: LosslessProvider, onDone: () -> Unit, onRestart: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(provider) {
        // Bootstrap uses the app's HTTP client; Chromium must use the same route
        // before loading or submitting the broker's network-bound challenge.
        if (!AppProxyWebView.prepare(context)) {
            failure = R.string.lossless_verify_proxy_unavailable
            return@LaunchedEffect
        }
        runCatching { provider.session.verificationUrl() }
            .onSuccess { if (it.isEmpty()) onDone() else url = it }
            .onFailure {
                if (it is CancellationException) throw it
                failure = R.string.lossless_verify_unreachable
            }
    }

    fun intercept(target: String): Boolean {
        if (target.startsWith("http://") || target.startsWith("https://")) return false
        val grant = ZarzSession.grantFrom(target) ?: target.takeIf { it.startsWith("gr_") }
        if (grant != null && !finishing && failure == null) {
            finishing = true
            scope.launch {
                runCatching { provider.session.completeGrant(grant) }
                    .onSuccess {
                        Toast.makeText(context, context.getString(R.string.lossless_verified_toast, provider.label), Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                    .onFailure {
                        if (it is CancellationException) throw it
                        failure = R.string.lossless_verify_failed
                        finishing = false
                    }
            }
        }
        // Any other non-web scheme (intent:, market:) is not this page's to open.
        return true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lossless_verify_title, provider.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = onRestart, enabled = !finishing && (url != null || failure != null)) {
                    Text(stringResource(R.string.lossless_verify_restart))
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
            }
            val current = url
            val error = failure
            if (error != null) {
                Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(error))
                }
            } else if (current == null || finishing) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = browserUserAgent(settings.userAgentString)
                            // The page's first choice: WebView drops its script-started
                            // mornye:// redirect, so it hands the grant to this bridge instead.
                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun postMessage(message: String) {
                                        post { intercept(message) }
                                    }
                                },
                                "MornyeGrant",
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedHttpAuthRequest(
                                    view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?,
                                ) {
                                    val local = AppWireGuard.credentialForWebView(host, realm)
                                    if (local != null) handler?.proceed(local.first, local.second)
                                    else handler?.cancel()
                                }

                                override fun onReceivedError(
                                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
                                ) {
                                    if (request?.isForMainFrame != true || finishing || intercept(request.url.toString())) return
                                    val code = error?.errorCode
                                    failure = if (code == ERROR_PROXY_AUTHENTICATION || (AppWireGuard.isReady && code == ERROR_CONNECT)) {
                                        R.string.lossless_verify_proxy_unavailable
                                    } else {
                                        R.string.lossless_verify_unreachable
                                    }
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?,
                                ) {
                                    if (!finishing && request?.isForMainFrame == true && response?.statusCode == 407) {
                                        failure = R.string.lossless_verify_proxy_unavailable
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                                    intercept(request.url.toString())

                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                    if (intercept(url)) view.stopLoading()
                                }
                            }
                            loadUrl(current)
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }
        }
    }
}
