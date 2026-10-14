package com.ytdash.studio

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.FrameLayout
import android.widget.TextView
import android.graphics.Color
import android.view.Gravity

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var offlineView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 12, 14)
        window.navigationBarColor = Color.rgb(12, 12, 14)
        window.decorView.systemUiVisibility = 0

        val root = FrameLayout(this)
        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(12, 12, 14))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    return url.scheme != "https" && url.scheme != "http"
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) {
                        offlineView.visibility = View.VISIBLE
                    }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    offlineView.visibility = View.GONE
                }
            }
            webChromeClient = WebChromeClient()
            loadUrl("https://ytdash-z-hub.vercel.app")
        }
        offlineView = TextView(this).apply {
            text = "Ytdash Studio\n\nCould not connect. Check your internet connection and reopen the app."
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(12, 12, 14))
            visibility = View.GONE
            setOnClickListener {
                visibility = View.GONE
                webView.reload()
            }
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(offlineView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
