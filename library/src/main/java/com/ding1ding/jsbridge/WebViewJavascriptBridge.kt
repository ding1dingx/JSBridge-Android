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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

  private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

  private var userWebViewClient: WebViewClient? = null

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
        userWebViewClient?.onPageFinished(view, url)
      }

      @Deprecated(
        "Deprecated in Java",
        ReplaceWith(
          "client?.shouldOverrideUrlLoading(view, url) ?: super.shouldOverrideUrlLoading(view, url)",
          "android.webkit.WebViewClient",
        ),
      )
      override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        userWebViewClient?.shouldOverrideUrlLoading(view, url)
          ?: super.shouldOverrideUrlLoading(view, url)

      // Add other WebViewClient methods as needed, delegating to the client if it's not null
    }
  }

  fun setUserWebViewClient(client: WebViewClient?) {
    this.userWebViewClient = client
  }

  @MainThread
  fun injectJavascriptIfNeeded() {
    if (isInjected.get() || !isWebViewReady.get()) {
      Logger.d {
        "JavaScript injection skipped. Injected: ${isInjected.get()}, WebView ready: ${isWebViewReady.get()}"
      }
      return
    }
    Logger.d { "Injecting JavaScript" }
    scope.launch {
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
    val callbackId = callback?.let { "native_cb_${uniqueId.incrementAndGet()}" }?.also {
      responseCallbacks[it] = callback
    }
    val message = CallMessage(handlerName, data, callbackId)
    val messageString = MessageSerializer.serializeCallMessage(message)
    dispatchMessage(messageString)
    Logger.d { "Handler called: $handlerName" }
  }

  private fun processMessage(messageString: String) {
    scope.launch {
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
    messageHandlers[message.handlerName]?.let { handler ->
      @Suppress("UNCHECKED_CAST")
      val responseData = (handler as MessageHandler<Any?, Any?>).handle(message.data)
      message.callbackId?.let { callbackId ->
        val response = ResponseMessage(callbackId, responseData, null, null, null)
        val responseString = MessageSerializer.serializeResponseMessage(response)
        dispatchMessage(responseString)
        Logger.d { "Request handled: ${message.handlerName}" }
      }
    } ?: Logger.e { "No handler found for: ${message.handlerName}" }
  }

  private fun dispatchMessage(messageString: String) {
    scope.launch {
      val script = "WebViewJavascriptBridge.handleMessageFromNative('$messageString');"
      webView.evaluateJavascript(script, null)
      Logger.d { "Message dispatched to JavaScript" }
    }
  }

  private fun loadAsset(fileName: String): String = runBlocking(Dispatchers.IO) {
    try {
      context.assets.open(fileName).bufferedReader().use { it.readText() }.trimIndent()
    } catch (e: Exception) {
      Logger.e(e) { "Error loading asset $fileName" }
      ""
    }
  }

  private fun clearState() {
    responseCallbacks.clear()
    uniqueId.set(0)
    isInjected.set(false)
    isWebViewReady.set(false)
    Logger.d { "Bridge state cleared" }
  }

  private fun release() {
    webView.removeJavascriptInterface("normalPipe")
    webView.removeJavascriptInterface("consolePipe")
    consolePipe = null
    responseCallbacks.clear()
    messageHandlers.clear()
    clearState()
    scope.cancel()
    Logger.d { "Bridge released" }
  }

  fun reinitialize() {
    release()
    setupBridge()
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
    @JvmStatic
    fun create(
      context: Context,
      webView: WebView,
      lifecycle: Lifecycle? = null,
      userWebViewClient: WebViewClient? = null,
    ): WebViewJavascriptBridge = WebViewJavascriptBridge(context, webView).apply {
      lifecycle?.addObserver(this)
      setUserWebViewClient(userWebViewClient)
      Logger.d { "Bridge created and lifecycle observer added" }
    }

    @JvmStatic
    fun setLogLevel(level: Logger.LogLevel) {
      Logger.logLevel = level
    }
  }
}
