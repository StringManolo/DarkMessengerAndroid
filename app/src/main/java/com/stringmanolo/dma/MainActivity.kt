package com.stringmanolo.dma

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Toast

class MainActivity : AppCompatActivity() {

  private lateinit var webView: WebView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webview)
    setupWebView()
  }

  private fun setupWebView() {
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true
    webView.settings.allowFileAccess = true

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return false
      }
    }

    webView.webChromeClient = WebChromeClient()
    webView.addJavascriptInterface(WebAppInterface(this), "dma")

    webView.loadUrl("file:///android_asset/dark-messenger-ui.html")
  }

  class WebAppInterface(private val context: android.content.Context) {

    @JavascriptInterface
    fun toast(message: String) {
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  override fun onBackPressed() {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      super.onBackPressed()
    }
  }
}
