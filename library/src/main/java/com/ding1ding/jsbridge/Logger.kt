package com.ding1ding.jsbridge

import android.util.Log
import kotlin.math.min

object Logger {
  private const val MAX_LOG_LENGTH = 4000
  const val DEFAULT_TAG = "WebViewJsBridge"

  @Volatile
  var logLevel = LogLevel.INFO

  enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR, NONE }

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
    if (logLevel <= LogLevel.ERROR) {
      val fullMessage = buildString {
        append(message())
        if (isNotEmpty() && message().isNotEmpty()) append(": ")
        append(Log.getStackTraceString(throwable))
      }
      logInternal(Log.ERROR, tag, fullMessage)
    }
  }

  @JvmStatic
  inline fun log(level: LogLevel, tag: String = DEFAULT_TAG, message: () -> String) {
    if (logLevel <= level) logInternal(level.toAndroidLogLevel(), tag, message())
  }

  fun logInternal(priority: Int, tag: String, message: String) {
    if (message.length < MAX_LOG_LENGTH) {
      Log.println(priority, tag, message)
      return
    }

    var i = 0
    val length = message.length
    while (i < length) {
      var newline = message.indexOf('\n', i)
      newline = if (newline != -1) newline else length
      do {
        val end = min(newline, i + MAX_LOG_LENGTH)
        val part = message.substring(i, end)
        Log.println(priority, tag, part)
        i = end
      } while (i < newline)
      i++
    }
  }

  fun LogLevel.toAndroidLogLevel() = when (this) {
    LogLevel.VERBOSE -> Log.VERBOSE
    LogLevel.DEBUG -> Log.DEBUG
    LogLevel.INFO -> Log.INFO
    LogLevel.WARN -> Log.WARN
    LogLevel.ERROR -> Log.ERROR
    LogLevel.NONE -> Log.ASSERT
  }
}
