package com.ding1ding.jsbridge

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.annotation.MainThread
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WebViewJavascriptBridge @JvmOverloads constructor(
  private val context: Context,
  private val webView: WebView,
  private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
  @JvmField
  var consolePipe: ConsolePipe? = null

  private val responseCallbacks = mutableMapOf<String, Callback<*>>()
  private val messageHandlers = mutableMapOf<String, MessageHandler<*, *>>()
  private val uniqueId = AtomicInteger(0)

  private val bridgeScript by lazy { loadAsset("bridge.js") }
  private val consoleHookScript by lazy { loadAsset("hookConsole.js") }

  private var isInjected = false

  init {
    setupBridge()
  }

  @JvmOverloads
  fun reset(clearHandlers: Boolean = false) = synchronized(this) {
    responseCallbacks.clear()
    if (clearHandlers) {
      messageHandlers.clear()
    }
    uniqueId.set(0)
    isInjected = false
  }

  fun release() {
    removeJavascriptInterface()
    consolePipe = null
    responseCallbacks.clear()
    messageHandlers.clear()
    coroutineScope.launch { /* Cancel all ongoing coroutines */ }.cancel()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupBridge() {
    webView.settings.javaScriptEnabled = true
    webView.addJavascriptInterface(this, "normalPipe")
    webView.addJavascriptInterface(this, "consolePipe")
  }

  private fun removeJavascriptInterface() = synchronized(this) {
    webView.removeJavascriptInterface("normalPipe")
    webView.removeJavascriptInterface("consolePipe")
    reset(true)
  }

  @JavascriptInterface
  fun postMessage(data: String?) {
    data?.let { processMessage(it) }
  }

  @JavascriptInterface
  fun receiveConsole(data: String?) {
    consolePipe?.post(data.orEmpty())
  }

  @MainThread
  fun injectJavascript() {
    if (!isInjected) {
      webView.evaluateJavascript("javascript:$bridgeScript", null)
      webView.evaluateJavascript("javascript:$consoleHookScript", null)
      isInjected = true
    }
  }

  fun registerHandler(handlerName: String, messageHandler: MessageHandler<*, *>) {
    synchronized(messageHandlers) {
      messageHandlers[handlerName] = messageHandler
    }
  }

  fun removeHandler(handlerName: String) {
    synchronized(messageHandlers) {
      messageHandlers.remove(handlerName)
    }
  }

  @JvmOverloads
  fun callHandler(handlerName: String, data: Any? = null, callback: Callback<*>? = null) {
    val callbackId = callback?.let { "native_cb_${uniqueId.incrementAndGet()}" }
    callbackId?.let { responseCallbacks[it] = callback }

    val message = CallMessage(handlerName, data, callbackId)
    val messageString = MessageSerializer.serializeCallMessage(message)
    dispatchMessage(messageString)
  }

  private fun processMessage(messageString: String) {
    coroutineScope.launch(Dispatchers.Default) {
      try {
        val message = MessageSerializer.deserializeResponseMessage(
          messageString,
          responseCallbacks,
          messageHandlers,
        )
        when {
          message.responseId != null -> handleResponse(message)
          else -> handleRequest(message)
        }
      } catch (e: Exception) {
        Log.e("[JsBridge]", "Error processing message: ${e.message}")
      }
    }
  }

  private suspend fun handleResponse(responseMessage: ResponseMessage) {
    val callback = responseCallbacks.remove(responseMessage.responseId)
    if (callback is Callback<*>) {
      @Suppress("UNCHECKED_CAST")
      (callback as Callback<Any?>).onResult(responseMessage.responseData)
    }
  }

  private suspend fun handleRequest(message: ResponseMessage) {
    val handler = messageHandlers[message.handlerName]
    if (handler is MessageHandler<*, *>) {
      @Suppress("UNCHECKED_CAST")
      val typedMessageHandler = handler as MessageHandler<Any?, Any?>
      val responseData = typedMessageHandler.handle(message.data)
      message.callbackId?.let { callbackId ->
        val response = ResponseMessage(callbackId, responseData, null, null, null)
        val responseString = MessageSerializer.serializeResponseMessage(response)
        dispatchMessage(responseString)
      }
    }
  }

  private fun dispatchMessage(messageString: String) {
    val script = "WebViewJavascriptBridge.handleMessageFromNative('$messageString');"
    webView.post { webView.evaluateJavascript(script, null) }
  }

  private fun loadAsset(fileName: String): String = runCatching {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
  }.getOrElse {
    Log.e("[JsBridge]", "Error loading asset $fileName: ${it.message}")
    ""
  }.trimIndent()
}
