package com.ding1ding.jsbridge

import android.util.Log
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import org.json.JSONObject

object MessageSerializer {
  private const val HANDLER_NAME = "handlerName"
  private const val DATA = "data"
  private const val CALLBACK_ID = "callbackId"
  private const val RESPONSE_ID = "responseId"
  private const val RESPONSE_DATA = "responseData"

  private val charsToReplace = mapOf(
    '\\' to "\\\\",
    '\"' to "\\\"",
    '\'' to "\\\'",
    '\n' to "\\n",
    '\r' to "\\r",
    '\u000C' to "\\u000C",
    '\u2028' to "\\u2028",
    '\u2029' to "\\u2029",
  )

  @Suppress("NOTHING_TO_INLINE")
  private inline fun String.escapeJavascript(): String = buildString(capacity = length) {
    for (char in this@escapeJavascript) {
      append(charsToReplace[char] ?: char)
    }
  }

  fun serializeCallMessage(message: CallMessage): String {
    val json = JSONObject()
    json.put(HANDLER_NAME, message.handlerName)
    message.data?.let { json.put(DATA, JsonUtils.toJson(it)) }
    message.callbackId?.let { json.put(CALLBACK_ID, it) }
    return json.toString().escapeJavascript()
  }

  fun serializeResponseMessage(message: ResponseMessage): String {
    val json = JSONObject()
    message.responseId?.let { json.put(RESPONSE_ID, it) }
    message.responseData?.let {
      json.put(
        RESPONSE_DATA,
        when (it) {
          is Map<*, *> -> JSONObject(
            it.mapKeys { entry -> entry.key.toString() }
              .mapValues { entry -> entry.value ?: JSONObject.NULL },
          )

          else -> JsonUtils.toJson(it)
        },
      )
    }
    message.callbackId?.let { json.put(CALLBACK_ID, it) }
    message.handlerName?.let { json.put(HANDLER_NAME, it) }
    message.data?.let { json.put(DATA, JsonUtils.toJson(it)) }
    return json.toString().escapeJavascript()
  }

  fun deserializeResponseMessage(
    jsonString: String,
    responseCallbacks: Map<String, Callback<*>>,
    messageHandlers: Map<String, Handler<*, *>>,
  ): ResponseMessage {
    val json = JSONObject(jsonString)
    val responseId = json.optString(RESPONSE_ID)

    return if (responseId.isNotEmpty()) {
      val callback = responseCallbacks[responseId]
      val targetType = callback?.javaClass?.genericInterfaces?.firstOrNull()?.let {
        (it as? ParameterizedType)?.actualTypeArguments?.firstOrNull()
      }
      ResponseMessage(
        responseId = responseId,
        responseData = parseData(json.optString(RESPONSE_DATA), targetType),
        callbackId = null,
        handlerName = null,
        data = null,
      )
    } else {
      val handlerName = json.getString(HANDLER_NAME)
      val handler = messageHandlers[handlerName]
      val targetType = handler?.javaClass?.genericInterfaces?.firstOrNull()?.let {
        (it as? ParameterizedType)?.actualTypeArguments?.firstOrNull()
      }
      ResponseMessage(
        responseId = null,
        responseData = null,
        callbackId = json.optString(CALLBACK_ID),
        handlerName = handlerName,
        data = parseData(json.optString(DATA), targetType),
      )
    }
  }

  private fun parseData(jsonString: String, targetType: Type?): Any? {
    val data = JsonUtils.fromJson(jsonString)
    return when (targetType) {
      null -> data
      is Class<*> -> createInstance(targetType, data)
      is ParameterizedType -> {
        val rawType = targetType.rawType as Class<*>
        when {
          Map::class.java.isAssignableFrom(rawType) -> data as? Map<*, *>
            ?: emptyMap<String, Any?>()

          List::class.java.isAssignableFrom(rawType) -> data as? List<*> ?: emptyList<Any?>()
          else -> createInstance(rawType, data)
        }
      }

      else -> data
    }
  }

  private fun createInstance(clazz: Class<*>, data: Any?): Any? = when {
    clazz.isAssignableFrom(data?.javaClass ?: Any::class.java) -> data
    data is Map<*, *> -> {
      try {
        val constructor = clazz.getDeclaredConstructor(
          *data.keys.map {
            it.toString()
          }.map { clazz.getDeclaredField(it).type }.toTypedArray(),
        )
        constructor.isAccessible = true
        constructor.newInstance(*data.values.toTypedArray())
      } catch (e: Exception) {
        Log.e("[JsBridge]", "Error creating instance of ${clazz.simpleName}: ${e.message}")
        data
      }
    }

    else -> data
  }
}
