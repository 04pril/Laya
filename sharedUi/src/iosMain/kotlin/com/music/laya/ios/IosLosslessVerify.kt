package com.music.laya.ios

import com.music.laya.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.music.laya.data.sources.lossless.LosslessProvider
import com.music.laya.data.sources.lossless.ZarzSession
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLErrorCancelled
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/** Catches the captcha page's hand-off to [ZarzSession.CALLBACK], however it is navigated to. */
private class GrantDelegate(
    val onCallback: (String) -> Unit,
    val onLoadFailure: () -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString.orEmpty()
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            onCallback(url)
        }
    }

    // A server redirect to an unknown scheme can surface only as a failed load.
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        val url = withError.userInfo["NSErrorFailingURLStringKey"] as? String
        if (url?.startsWith(ZarzSession.CALLBACK) == true) onCallback(url)
        else if (withError.code != NSURLErrorCancelled) onLoadFailure()
    }
}

/** The page's preferred hand-off: it posts the grant to `window.MornyeGrant` when one exists. */
private class GrantBridge(val onCallback: (String) -> Unit) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        (didReceiveScriptMessage.body as? String)?.let(onCallback)
    }
}

/** Full-screen broker check for [provider], with a fresh challenge on retry. */
@Composable
internal fun IosLosslessVerifyScreen(provider: LosslessProvider, onClose: () -> Unit) {
    var attempt by remember(provider) { mutableIntStateOf(0) }
    key(provider, attempt) {
        IosLosslessVerificationAttempt(provider, onClose, onRestart = { attempt++ })
    }
}

@Composable
@OptIn(ExperimentalForeignApi::class)
private fun IosLosslessVerificationAttempt(provider: LosslessProvider, onClose: () -> Unit, onRestart: () -> Unit) {
    val scope = rememberCoroutineScope()
    val failedText = stringResource(Res.string.lossless_verify_failed)
    val unreachableText = stringResource(Res.string.lossless_verify_unreachable)
    val proxyUnavailable = stringResource(Res.string.lossless_verify_proxy_unavailable)
    var failure by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }
    val onCallback: (String) -> Unit = { url ->
        val grant = ZarzSession.grantFrom(url) ?: url.takeIf { it.startsWith("gr_") }
        if (grant != null && !finishing && failure == null) {
            finishing = true
            scope.launch {
                runCatching { provider.session.completeGrant(grant) }
                    .onSuccess { onClose() }
                    .onFailure {
                        if (it is CancellationException) throw it
                        failure = failedText
                        finishing = false
                    }
            }
        }
    }
    val delegate = remember {
        GrantDelegate(onCallback, onLoadFailure = { if (!finishing) failure = unreachableText })
    }
    val bridge = remember { GrantBridge { onCallback(it) } }
    val webView = remember {
        val configuration = WKWebViewConfiguration().apply {
            websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            userContentController.addScriptMessageHandler(bridge, "mornyeGrant")
            userContentController.addUserScript(
                WKUserScript(
                    source = "window.MornyeGrant={postMessage:function(m){window.webkit.messageHandlers.mornyeGrant.postMessage(String(m))}};",
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                ),
            )
        }
        WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = configuration)
            .also { it.customUserAgent = SAFARI_AGENT; it.navigationDelegate = delegate }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.navigationDelegate = null
            webView.stopLoading()
            webView.configuration.userContentController.removeScriptMessageHandlerForName("mornyeGrant")
        }
    }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(provider) {
        // The app's URLSession proxy hooks do not configure WebKit.
        if (IosNative.host?.configureBrowser(webView.configuration.websiteDataStore) == false) {
            failure = proxyUnavailable
            return@LaunchedEffect
        }
        runCatching { provider.session.verificationUrl() }
            .onSuccess { url ->
                if (url.isEmpty()) onClose() else {
                    val requestUrl = NSURL.URLWithString(url)
                    if (requestUrl == null) {
                        failure = unreachableText
                    } else {
                        ready = true
                        webView.loadRequest(NSURLRequest.requestWithURL(requestUrl))
                    }
                }
            }
            .onFailure {
                if (it is CancellationException) throw it
                failure = unreachableText
            }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.close)) }
                Text(
                    stringResource(Res.string.lossless_verify_title, provider.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRestart, enabled = !finishing && (ready || failure != null)) {
                    Text(stringResource(Res.string.lossless_verify_restart))
                }
            }
            val error = failure
            if (ready && !finishing && error == null) {
                UIKitView(factory = { webView }, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error == null) CircularProgressIndicator() else Text(
                        error,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
