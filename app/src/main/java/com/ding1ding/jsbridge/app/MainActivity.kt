package com.ding1ding.jsbridge.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.ding1ding.jsbridge.Callback
import com.ding1ding.jsbridge.ConsolePipe
import com.ding1ding.jsbridge.Handler
import com.ding1ding.jsbridge.WebViewJavascriptBridge
import com.ding1ding.jsbridge.model.Person

class MainActivity :
  AppCompatActivity(),
  View.OnClickListener {

  private lateinit var webView: WebView
  private lateinit var bridge: WebViewJavascriptBridge

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    setupWebView()
    setupBridge()
    setupClickListeners()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupWebView() {
    webView = findViewById<WebView>(R.id.webView).apply {
      WebView.setWebContentsDebuggingEnabled(true)
      settings.javaScriptEnabled = true
      settings.allowUniversalAccessFromFileURLs = true
      webViewClient = createWebViewClient()
      loadUrl("file:///android_asset/index.html")
    }
  }

  private fun setupBridge() {
    bridge = WebViewJavascriptBridge(this, webView).apply {
      consolePipe = object : ConsolePipe {
        override fun post(message: String) {
          Log.d("[console.log]", message)
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

  private fun createWebViewClient() = object : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      Log.d(TAG, "onPageStarted")
      bridge.injectJavascript()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      Log.d(TAG, "onPageFinished")
    }
  }

  private fun createDeviceLoadHandler() = object : Handler<Map<String, String>, Any> {
    override fun handle(parameter: Map<String, String>): Any {
      Log.d(TAG, "DeviceLoadJavascriptSuccess, $parameter")
      return mapOf("result" to "Android")
    }
  }

  private fun createObjTestHandler() = object : Handler<Map<String, Any>, Map<String, Any>> {
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
      Person("Hayring", 23),
      object : Callback<Any> {
        override fun onResult(result: Any) {
          Log.d(TAG, "$handlerName, $result")
        }
      },
    )
  }

  companion object {
    private const val TAG = "MainActivity"
  }
}
