package com.fintrack.app.ui.auth

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Recuperación de contraseña con la página web oficial, pero abierta DENTRO
 * de la app (WebView): el usuario nunca sale a otro navegador.
 */
object RecoveryUrls {
    const val PASSWORD_RECOVERY = "https://calendar-04yk.onrender.com/routes/recob_pass.html"
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RecoveryWebScreen(
    url: String = RecoveryUrls.PASSWORD_RECOVERY,
    onBack: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recuperar contraseña") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) webView?.goBack() else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    loadUrl(url)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}
