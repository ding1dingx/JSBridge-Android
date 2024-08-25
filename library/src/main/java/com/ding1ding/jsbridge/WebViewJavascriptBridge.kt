package com.ding1ding.jsbridge

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class WebViewJavascriptBridge private constructor(
  private val context: Context,
  private val webView: WebView,
) : DefaultLifecycleObserver {

  var consolePipe: ConsolePipe? = null

  private val responseCallbacks = ConcurrentHashMap<String, Callback<*>>()
  private val messageHandlers = ConcurrentHashMap<String, MessageHandler<*, *>>()
  private val uniqueId = AtomicInteger(0)

  private val bridgeScript by lazy { loadAsset("bridge.js") }
  private val consoleHookScript by lazy { loadAsset("hookConsole.js") }

  private val isInjected = AtomicBoolean(false)
  private val isWebViewReady = AtomicBoolean(false)

  init {
    setupBridge()
    setupWebViewClient()
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun setupBridge() {
    webView.settings.javaScriptEnabled = true
    webView.addJavascriptInterface(JsBridgeInterface(), "normalPipe")
    webView.addJavascriptInterface(JsBridgeInterface(), "consolePipe")
    Logger.d { "Bridge setup completed" }
  }

  private fun setupWebViewClient() {
    webView.webViewClient = object : WebViewClient() {
      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        isWebViewReady.set(true)
        Logger.d { "WebView page finished loading" }
        injectJavascriptIfNeeded()
      }
    }
  }

  @MainThread
  private fun injectJavascriptIfNeeded() {
    if (isInjected.get() || !isWebViewReady.get()) {
      Logger.d {
        "JavaScript injection skipped. Injected: ${isInjected.get()}, WebView ready: ${isWebViewReady.get()}"
      }
      return
    }
    Logger.d { "Injecting JavaScript" }
    webView.post {
      webView.evaluateJavascript("javascript:$bridgeScript", null)
      webView.evaluateJavascript("javascript:$consoleHookScript", null)
      isInjected.set(true)
      Logger.d { "JavaScript injection completed" }
    }
  }

  fun registerHandler(handlerName: String, messageHandler: MessageHandler<*, *>) {
    messageHandlers[handlerName] = messageHandler
    Logger.d { "Handler registered: $handlerName" }
  }

  fun removeHandler(handlerName: String) {
    messageHandlers.remove(handlerName)
    Logger.d { "Handler removed: $handlerName" }
  }

  @JvmOverloads
  fun callHandler(handlerName: String, data: Any? = null, callback: Callback<*>? = null) {
    if (!isInjected.get()) {
      Logger.e { "Bridge is not injected. Cannot call handler: $handlerName" }
      return
    }
    val callbackId = callback?.let { "native_cb_${uniqueId.incrementAndGet()}" }
    callbackId?.let { responseCallbacks[it] = callback }

    val message = CallMessage(handlerName, data, callbackId)
    val messageString = MessageSerializer.serializeCallMessage(message)
    dispatchMessage(messageString)
    Logger.d { "Handler called: $handlerName" }
  }

  private fun processMessage(messageString: String) {
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
      Logger.e(e) { "Error processing message" }
    }
  }

  private fun handleResponse(responseMessage: ResponseMessage) {
    val callback = responseCallbacks.remove(responseMessage.responseId)
    if (callback is Callback<*>) {
      @Suppress("UNCHECKED_CAST")
      (callback as Callback<Any?>).onResult(responseMessage.responseData)
      Logger.d { "Response handled for ID: ${responseMessage.responseId}" }
    }
  }

  private fun handleRequest(message: ResponseMessage) {
    val handler = messageHandlers[message.handlerName]
    if (handler is MessageHandler<*, *>) {
      @Suppress("UNCHECKED_CAST")
      val typedMessageHandler = handler as MessageHandler<Any?, Any?>
      val responseData = typedMessageHandler.handle(message.data)
      message.callbackId?.let { callbackId ->
        val response = ResponseMessage(callbackId, responseData, null, null, null)
        val responseString = MessageSerializer.serializeResponseMessage(response)
        dispatchMessage(responseString)
        Logger.d { "Request handled: ${message.handlerName}" }
      }
    } else {
      Logger.e { "No handler found for: ${message.handlerName}" }
    }
  }

  private fun dispatchMessage(messageString: String) {
    val script = "WebViewJavascriptBridge.handleMessageFromNative('$messageString');"
    webView.post {
      webView.evaluateJavascript(script, null)
      Logger.d { "Message dispatched to JavaScript" }
    }
  }

  private fun loadAsset(fileName: String): String = try {
    context.assets.open(fileName).bufferedReader().use { it.readText() }
  } catch (e: Exception) {
    Logger.e(e) { "Error loading asset $fileName" }
    ""
  }.trimIndent()

  private fun clearState() {
    responseCallbacks.clear()
    uniqueId.set(0)
    isInjected.set(false)
    isWebViewReady.set(false)
    Logger.d { "Bridge state cleared" }
  }

  private fun removeJavascriptInterface() {
    webView.removeJavascriptInterface("normalPipe")
    webView.removeJavascriptInterface("consolePipe")
    Logger.d { "JavaScript interfaces removed" }
  }

  private fun release() {
    removeJavascriptInterface()
    consolePipe = null
    responseCallbacks.clear()
    messageHandlers.clear()
    clearState()
    Logger.d { "Bridge released" }
  }

  fun reinitialize() {
    release()
    setupBridge()
    setupWebViewClient()
    Logger.d { "Bridge reinitialized" }
  }

  override fun onResume(owner: LifecycleOwner) {
    Logger.d { "onResume" }
    injectJavascriptIfNeeded()
  }

  override fun onDestroy(owner: LifecycleOwner) {
    Logger.d { "onDestroy" }
    release()
  }

  private inner class JsBridgeInterface {
    @JavascriptInterface
    fun postMessage(data: String?) {
      data?.let {
        Logger.d { "Message received from JavaScript: $it" }
        processMessage(it)
      }
    }

    @JavascriptInterface
    fun receiveConsole(data: String?) {
      Logger.d { "Console message received: $data" }
      consolePipe?.post(data.orEmpty())
    }
  }

  companion object {
    fun create(
      context: Context,
      webView: WebView,
      lifecycle: Lifecycle? = null,
    ): WebViewJavascriptBridge = WebViewJavascriptBridge(context, webView).also { bridge ->
      lifecycle?.addObserver(bridge)
      Logger.d { "Bridge created and lifecycle observer added" }
    }

    fun setLogLevel(level: Logger.LogLevel) {
      Logger.logLevel = level
    }
  }
}
