package com.ding1ding.jsbridge.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.ding1ding.jsbridge.Callback
import com.ding1ding.jsbridge.ConsolePipe
import com.ding1ding.jsbridge.Logger
import com.ding1ding.jsbridge.MessageHandler
import com.ding1ding.jsbridge.WebViewJavascriptBridge
import com.ding1ding.jsbridge.model.Person

class MainActivity :
  AppCompatActivity(),
  View.OnClickListener {

  private lateinit var webView: WebView
  private lateinit var bridge: WebViewJavascriptBridge

  private val webViewContainer: LinearLayout by lazy { findViewById(R.id.linearLayout) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    if (BuildConfig.DEBUG) {
      WebViewJavascriptBridge.setLogLevel(Logger.LogLevel.VERBOSE)
    } else {
      WebViewJavascriptBridge.setLogLevel(Logger.LogLevel.ERROR)
    }

    setupWebView()
    setupClickListeners()
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK && event?.repeatCount == 0) {
      when {
        webView.canGoBack() -> webView.goBack()
        else -> supportFinishAfterTransition()
      }
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onResume() {
    super.onResume()
    webView.onResume()
    webView.resumeTimers()
  }

  override fun onPause() {
    webView.onPause()
    webView.pauseTimers()
    super.onPause()
  }

  override fun onDestroy() {
    releaseWebView()
    Logger.d(TAG) { "onDestroy" }
    super.onDestroy()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    webView = WebView(this).apply {
      removeJavascriptInterface("searchBoxJavaBridge_")
      removeJavascriptInterface("accessibility")
      removeJavascriptInterface("accessibilityTraversal")

      WebView.setWebContentsDebuggingEnabled(true)

      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        0,
        1f,
      )

      settings.apply {
        javaScriptEnabled = true
        allowUniversalAccessFromFileURLs = true
      }

      webViewClient = mWebViewClient
    }

    webViewContainer.addView(webView)

    setupWebViewBridge(webView)

    webView.loadUrl("file:///android_asset/index.html")
  }

  private fun setupWebViewBridge(webView: WebView) {
    bridge = WebViewJavascriptBridge.create(
      context = this,
      webView = webView,
      lifecycle = lifecycle,
      userWebViewClient = mWebViewClient,
    ).apply {
      consolePipe = object : ConsolePipe {
        override fun post(message: String) {
          Logger.v("[console.log]") { message }
        }
      }

      registerHandler("DeviceLoadJavascriptSuccess", createDeviceLoadHandler())
      registerHandler("ObjTest", createObjTestHandler())
    }
  }

  private fun setupClickListeners() {
    listOf(R.id.buttonSync, R.id.buttonAsync, R.id.objTest).forEach {
      findViewById<View>(it).setOnClickListener(this)
    }
  }

  private val mWebViewClient = object : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      super.onPageStarted(view, url, favicon)
      Logger.d(TAG) { "onPageStarted" }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)
      Logger.d(TAG) { "onPageFinished" }
    }
  }

  private fun createDeviceLoadHandler() = object : MessageHandler<Map<String, String>, Any> {
    override fun handle(parameter: Map<String, String>): Any {
      Logger.d(TAG) { "DeviceLoadJavascriptSuccess, $parameter" }
      return mapOf("result" to "Android")
    }
  }

  private fun createObjTestHandler() = object : MessageHandler<Map<String, Any>, Map<String, Any>> {
    override fun handle(parameter: Map<String, Any>): Map<String, Any> {
      val name = parameter["name"] as? String ?: ""
      val age = (parameter["age"] as? Number)?.toInt() ?: 0
      return mapOf("name" to name, "age" to age)
    }
  }

  override fun onClick(v: View?) {
    when (v?.id) {
      R.id.buttonSync -> callJsHandler("GetToken")
      R.id.buttonAsync -> callJsHandler("AsyncCall")
      R.id.objTest -> bridge.callHandler(
        "TestJavascriptCallNative",
        mapOf("message" to "Hello from Android"),
        null,
      )
    }
  }

  private fun callJsHandler(handlerName: String) {
    bridge.callHandler(
      handlerName,
      Person("Wukong", 23),
      object : Callback<Any> {
        override fun onResult(result: Any) {
          Logger.d(TAG) { "$handlerName, $result" }
        }
      },
    )
  }

  private fun releaseWebView() {
    webViewContainer.removeView(webView)
    webView.apply {
      stopLoading()
      loadUrl("about:blank")
      clearHistory()
      removeAllViews()
      webChromeClient = null
      // webViewClient = null
      settings.javaScriptEnabled = false
      destroy()
    }
  }

  companion object {
    private const val TAG = "MainActivity"
  }
}
