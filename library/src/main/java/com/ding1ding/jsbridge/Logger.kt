@file:Suppress("ktlint:standard:max-line-length")

package com.ding1ding.jsbridge

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

object Logger {
  const val DEFAULT_TAG = "WebViewJsBridge"

  var logLevel = AtomicInteger(LogLevel.INFO.value)

  enum class LogLevel(val value: Int) {
    VERBOSE(Log.VERBOSE),
    DEBUG(Log.DEBUG),
    INFO(Log.INFO),
    WARN(Log.WARN),
    ERROR(Log.ERROR),
    NONE(Log.ASSERT),
  }

  @JvmStatic
  inline fun v(tag: String = DEFAULT_TAG, message: () -> String) =
    log(LogLevel.VERBOSE, tag, message)

  @JvmStatic
  inline fun d(tag: String = DEFAULT_TAG, message: () -> String) = log(LogLevel.DEBUG, tag, message)

  @JvmStatic
  inline fun i(tag: String = DEFAULT_TAG, message: () -> String) = log(LogLevel.INFO, tag, message)

  @JvmStatic
  inline fun w(tag: String = DEFAULT_TAG, message: () -> String) = log(LogLevel.WARN, tag, message)

  @JvmStatic
  inline fun e(tag: String = DEFAULT_TAG, message: () -> String) = log(LogLevel.ERROR, tag, message)

  @JvmStatic
  inline fun e(throwable: Throwable, tag: String = DEFAULT_TAG, message: () -> String = { "" }) {
    if (logLevel.get() <= LogLevel.ERROR.value) {
      val fullMessage = buildString {
        append(message())
        if (isNotEmpty() && message().isNotEmpty()) append(": ")
        append(Log.getStackTraceString(throwable))
      }
      logInternal(LogLevel.ERROR, tag, fullMessage)
    }
  }

  @JvmStatic
  inline fun log(level: LogLevel, tag: String = DEFAULT_TAG, message: () -> String) {
    if (logLevel.get() <= level.value) logInternal(level, tag, message())
  }

  fun logInternal(level: LogLevel, tag: String, message: String) {
    Log.println(level.value, tag, message)
  }
}
