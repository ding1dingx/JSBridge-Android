package com.ding1ding.jsbridge

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import org.json.JSONObject

object MessageSerializer {
  private const val HANDLER_NAME = "handlerName"
  private const val DATA = "data"
  private const val CALLBACK_ID = "callbackId"
  private const val RESPONSE_ID = "responseId"
  private const val RESPONSE_DATA = "responseData"

  fun serializeCallMessage(message: CallMessage): String {
    val json = JSONObject()
    json.put(HANDLER_NAME, message.handlerName)
    message.data?.let { json.put(DATA, JsonUtils.toJson(it)) }
    message.callbackId?.let { json.put(CALLBACK_ID, it) }
    return json.toString()
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

  fun serializeResponseMessage(message: ResponseMessage): String {
    val json = JSONObject()
    message.responseId?.let { json.put(RESPONSE_ID, it) }
    message.responseData?.let {
      when (it) {
        is Map<*, *> -> json.put(RESPONSE_DATA, JSONObject(it as Map<String, Any>))
        else -> json.put(RESPONSE_DATA, JsonUtils.toJson(it))
      }
    }
    message.callbackId?.let { json.put(CALLBACK_ID, it) }
    message.handlerName?.let { json.put(HANDLER_NAME, it) }
    message.data?.let { json.put(DATA, JsonUtils.toJson(it)) }
    return json.toString()
  }

  private fun parseData(jsonString: String, targetType: Type?): Any? {
    val data = JsonUtils.fromJson(jsonString)
    return when {
      targetType == null -> data
      targetType is Class<*> -> createInstance(targetType, data)
      targetType is ParameterizedType -> {
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
        println("Error creating instance of ${clazz.simpleName}: ${e.message}")
        data
      }
    }

    else -> data
  }
}
