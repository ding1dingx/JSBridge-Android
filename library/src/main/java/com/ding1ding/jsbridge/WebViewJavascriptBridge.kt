package com.ding1ding.jsbridge

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView

class WebViewJavascriptBridge(private val context: Context, private val webView: WebView) {

  var consolePipe: ConsolePipe? = null

  private val responseCallbacks = mutableMapOf<String, Callback<*>>()
  private val messageHandlers = mutableMapOf<String, MessageHandler<*, *>>()
  private var uniqueId = 0

  init {
    setupBridge()
  }

  fun reset() {
    responseCallbacks.clear()
    messageHandlers.clear()
    uniqueId = 0
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupBridge() {
    webView.settings.javaScriptEnabled = true
    webView.addJavascriptInterface(this, "normalPipe")
    webView.addJavascriptInterface(this, "consolePipe")
  }

  fun removeJavascriptInterface() {
    webView.removeJavascriptInterface("normalPipe")
    webView.removeJavascriptInterface("consolePipe")
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
    val bridgeScript = loadAsset("bridge.js").trimIndent()
    val consoleHookScript = loadAsset("hookConsole.js").trimIndent()
    webView.post {
      webView.evaluateJavascript("javascript:$bridgeScript", null)
      webView.evaluateJavascript("javascript:$consoleHookScript", null)
    }
  }

  fun registerHandler(handlerName: String, messageHandler: MessageHandler<*, *>) {
    messageHandlers[handlerName] = messageHandler
  }

  fun removeHandler(handlerName: String) {
    messageHandlers.remove(handlerName)
  }

  fun callHandler(handlerName: String, data: Any? = null, callback: Callback<*>? = null) {
    val callbackId = callback?.let { "native_cb_${++uniqueId}" }
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
      Log.e("[JsBridge]", "Error processing message: ${e.message}")
    }
  }

  private fun handleResponse(responseMessage: ResponseMessage) {
    when (val callback = responseCallbacks.remove(responseMessage.responseId)) {
      is Callback<*> -> {
        @Suppress("UNCHECKED_CAST")
        (callback as Callback<Any?>).onResult(responseMessage.responseData)
      }

      else -> Log.w(
        "[JsBridge]",
        "Callback not found or has invalid type for responseId: ${responseMessage.responseId}",
      )
    }
  }

  private fun handleRequest(message: ResponseMessage) {
    when (val handler = messageHandlers[message.handlerName]) {
      is MessageHandler<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        val typedMessageHandler = handler as MessageHandler<Any?, Any?>
        val responseData = typedMessageHandler.handle(message.data)
        message.callbackId?.let { callbackId ->
          val response = ResponseMessage(callbackId, responseData, null, null, null)
          val responseString = MessageSerializer.serializeResponseMessage(response)
          dispatchMessage(responseString)
        }
      }

      else -> Log.w(
        "[JsBridge]",
        "Handler not found or has invalid type for handlerName: ${message.handlerName}",
      )
    }
  }

  private fun dispatchMessage(messageString: String) {
    val script = "WebViewJavascriptBridge.handleMessageFromNative('$messageString');"
    webView.post { webView.evaluateJavascript(script, null) }
  }

  private fun loadAsset(fileName: String): String = try {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
  } catch (e: Exception) {
    Log.e("[JsBridge]", "Error loading asset $fileName: ${e.message}")
    ""
  }
}
