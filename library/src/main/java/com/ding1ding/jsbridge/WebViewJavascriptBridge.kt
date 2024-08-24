package com.ding1ding.jsbridge

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView

class WebViewJavascriptBridge(private val context: Context, private val webView: WebView) {
  var consolePipe: ConsolePipe? = null
  private val responseCallbacks = mutableMapOf<String, Callback<*>>()
  private val messageHandlers = mutableMapOf<String, Handler<*, *>>()
  private var uniqueId = 0

  init {
    setupBridge()
  }

  @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
  private fun setupBridge() {
    webView.settings.javaScriptEnabled = true
    webView.addJavascriptInterface(this, "normalPipe")
    webView.addJavascriptInterface(this, "consolePipe")
  }

  @JavascriptInterface
  fun postMessage(data: String?) {
    data?.let { processMessage(it) }
  }

  @JavascriptInterface
  fun receiveConsole(data: String?) {
    consolePipe?.post(data.orEmpty())
  }

  fun injectJavascript() {
    val bridgeScript = loadAsset("bridge.js")
    val consoleHookScript = loadAsset("hookConsole.js")
    webView.post {
      webView.evaluateJavascript("javascript:$bridgeScript", null)
      webView.evaluateJavascript("javascript:$consoleHookScript", null)
    }
  }

  fun registerHandler(handlerName: String, handler: Handler<*, *>) {
    messageHandlers[handlerName] = handler
  }

  fun removeHandler(handlerName: String) {
    messageHandlers.remove(handlerName)
  }

  fun callHandler(handlerName: String, data: Any? = null, callback: Callback<*>? = null) {
    val callbackId = callback?.let { "${++uniqueId}" }
    callbackId?.let { responseCallbacks[it] = callback }

    val message = CallMessage(handlerName, data, callbackId)
    val messageString = MessageSerializer.serializeCallMessage(message)
    dispatchMessage(messageString)
  }

  private fun processMessage(messageString: String) {
    try {
      val message = MessageSerializer.deserializeResponseMessage(
        messageString,
        responseCallbacks,
        messageHandlers,
      )
      if (message.responseId != null) {
        handleResponse(message)
      } else {
        handleRequest(message)
      }
    } catch (e: Exception) {
      println("Error processing message: ${e.message}")
    }
  }

  private fun handleResponse(responseMessage: ResponseMessage) {
    val callback = responseCallbacks.remove(responseMessage.responseId) as? Callback<Any?>
    callback?.onResult(responseMessage.responseData)
  }

  private fun handleRequest(message: ResponseMessage) {
    val handler = messageHandlers[message.handlerName] as? Handler<Any?, Any?>
    handler?.let {
      val responseData = it.handle(message.data)
      message.callbackId?.let { callbackId ->
        val response = ResponseMessage(callbackId, responseData, null, null, null)
        val responseString = MessageSerializer.serializeResponseMessage(response)
        dispatchMessage(responseString)
      }
    }
  }

  private fun dispatchMessage(messageString: String) {
    val escapedMessage = messageString.replace("\\", "\\\\").replace("\"", "\\\"")
    val javascript = "WebViewJavascriptBridge.handleMessageFromNative('$escapedMessage');"
    webView.post { webView.evaluateJavascript(javascript, null) }
  }

  private fun loadAsset(fileName: String): String = try {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
  } catch (e: Exception) {
    println("Error loading asset $fileName: ${e.message}")
    ""
  }
}
