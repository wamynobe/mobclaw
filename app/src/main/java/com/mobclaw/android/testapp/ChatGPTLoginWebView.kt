package com.mobclaw.android.testapp

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatGPTLoginWebView(
    authUrl: String,
    onAuthSuccess: (String) -> Unit,
    onAuthCancel: () -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        if (url.startsWith("http://localhost:1455/auth/callback")) {
                            val uri = Uri.parse(url)
                            val code = uri.getQueryParameter("code")
                            if (code != null) {
                                onAuthSuccess(code)
                            } else {
                                onAuthCancel()
                            }
                            return true // block the actual "localhost" navigation
                        }
                        
                        return false // let webview load everything else
                    }
                }
            }
        },
        update = { webView ->
            webView.loadUrl(authUrl)
        }
    )
}
