package com.ding1ding.jsbridge.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.ding1ding.jsbridge.Callback
import com.ding1ding.jsbridge.ConsolePipe
import com.ding1ding.jsbridge.Handler
import com.ding1ding.jsbridge.WebViewJavascriptBridge
import java.lang.reflect.InvocationTargetException

class MainActivity :
  AppCompatActivity(),
  View.OnClickListener {

  private val TAG = "MainActivity"

  private var mWebView: WebView? = null
  private var bridge: WebViewJavascriptBridge? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    setupView()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupView() {
    val buttonSync = findViewById<Button>(R.id.buttonSync)
    val buttonAsync = findViewById<Button>(R.id.buttonAsync)
    val objTest = findViewById<Button>(R.id.objTest)

    mWebView = findViewById(R.id.webView)
    WebView.setWebContentsDebuggingEnabled(true)

    setAllowUniversalAccessFromFileURLs(mWebView!!)

    buttonSync.setOnClickListener(this)
    buttonAsync.setOnClickListener(this)
    objTest.setOnClickListener(this)

    bridge = WebViewJavascriptBridge(this, mWebView!!)

    bridge?.consolePipe = object : ConsolePipe {
      override fun post(message: String) {
        Log.d("[console.log]", message)
      }
    }

    bridge?.registerHandler(
      "DeviceLoadJavascriptSuccess",
      object : Handler<Map<String, String>, Any> {

        override fun handle(parameter: Map<String, String>): Any {
          Log.d(TAG, "DeviceLoadJavascriptSuccess, $parameter")
          return mapOf("result" to "Android")
        }
      },
    )

    bridge?.registerHandler(
      "ObjTest",
      object : Handler<Map<String, Any>, Map<String, Any>> {
        override fun handle(parameter: Map<String, Any>): Map<String, Any> {
          val name = parameter["name"] as? String ?: ""
          val age = (parameter["age"] as? Number)?.toInt() ?: 0
          return mapOf(
            "name" to name,
            "age" to (age + 1),
          )
        }
      },
    )

    mWebView?.webViewClient = webClient
    mWebView?.loadUrl("file:///android_asset/index.html")
  }

  private val webClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
      Log.d(TAG, "shouldOverrideUrlLoading")
      return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      Log.d(TAG, "onPageStarted")
      bridge?.injectJavascript()
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      Log.d(TAG, "onPageFinished")
    }
  }

  override fun onClick(v: View?) {
    when (v?.id) {
      R.id.buttonSync -> {
        val data = Person(
          "Hayring",
          23,
        )
        // call js Sync function
        bridge?.callHandler(
          "GetToken",
          data,
          object : Callback<Map<String, Any?>> {

            override fun onResult(result: Map<String, Any?>) {
              Log.d(TAG, "GetToken, $result")
            }
          },
        )
      }

      R.id.buttonAsync -> {
        val data = Person(
          "Hayring",
          23,
        )
        // call js Async function
        bridge?.callHandler(
          "AsyncCall",
          data,
          object : Callback<Map<String, Any?>> {

            override fun onResult(result: Map<String, Any?>) {
              Log.d(TAG, "AsyncCall, $result")
            }
          },
        )
      }

      R.id.objTest -> {
        bridge?.callHandler(
          "TestJavascriptCallNative",
          mapOf("message" to "Hello from Android"),
          null,
        )
      }
    }
  }

  // Allow Cross Domain
  private fun setAllowUniversalAccessFromFileURLs(webView: WebView) {
    try {
      val clazz: Class<*> = webView.settings.javaClass
      val method = clazz.getMethod(
        "setAllowUniversalAccessFromFileURLs",
        Boolean::class.javaPrimitiveType,
      )
      method.invoke(webView.settings, true)
    } catch (e: IllegalArgumentException) {
      e.printStackTrace()
    } catch (e: NoSuchMethodException) {
      e.printStackTrace()
    } catch (e: IllegalAccessException) {
      e.printStackTrace()
    } catch (e: InvocationTargetException) {
      e.printStackTrace()
    }
  }
}
