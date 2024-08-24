package com.ding1ding.jsbridge

import android.util.Log
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {
  private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }

  fun toJson(any: Any?): String = when (any) {
    null -> "null"
    is JSONObject, is JSONArray -> any.toString()
    is String -> JSONObject.quote(any)
    is Boolean, is Number -> any.toString()
    is Date -> JSONObject.quote(isoDateFormat.format(any))
    is Map<*, *> -> any.toJsonObject().toString()
    is Collection<*> -> any.toJsonArray().toString()
    is Enum<*> -> JSONObject.quote(any.name)
    else -> try {
      any::class.java.declaredFields
        .filter { !it.isSynthetic }
        .associate { field ->
          field.isAccessible = true
          field.name to field.get(any)
        }.toJsonObject().toString()
    } catch (e: Exception) {
      Log.e("[JsBridge]", "Failed to serialize object of type ${any::class.java.simpleName}", e)
      JSONObject.quote(any.toString())
    }
  }

  private fun Map<*, *>.toJsonObject() = JSONObject().apply {
    forEach { (key, value) ->
      put(key.toString(), value?.let { toJsonValue(it) } ?: JSONObject.NULL)
    }
  }

  private fun Collection<*>.toJsonArray() = JSONArray(
    map {
      it?.let { toJsonValue(it) }
        ?: JSONObject.NULL
    },
  )

  private fun toJsonValue(value: Any): Any = when (value) {
    is JSONObject, is JSONArray, is String, is Boolean, is Number -> value
    else -> toJson(value)
  }

  fun fromJson(json: String): Any? = try {
    when {
      json == "null" -> null
      json.startsWith("{") && json.endsWith("}") -> JSONObject(json).toMap()
      json.startsWith("[") && json.endsWith("]") -> JSONArray(json).toList()
      json.startsWith("\"") && json.endsWith("\"") -> json.unquote().let { unquoted ->
        tryParseDate(unquoted) ?: unquoted
      }

      json == "true" -> true
      json == "false" -> false
      else -> parseNumber(json)
    }
  } catch (e: Exception) {
    Log.e("[JsBridge]", "Error parsing JSON: $json", e)
    json // Return the original string if parsing fails
  }

  private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { key ->
    when (val value = get(key)) {
      is JSONObject -> value.toMap()
      is JSONArray -> value.toList()
      JSONObject.NULL -> null
      else -> value
    }
  }

  private fun JSONArray.toList(): List<Any?> = (0 until length()).map { index ->
    when (val value = get(index)) {
      is JSONObject -> value.toMap()
      is JSONArray -> value.toList()
      JSONObject.NULL -> null
      else -> value
    }
  }

  private fun parseNumber(value: String): Any = when {
    value.contains(".") || value.lowercase(Locale.US).contains("e") -> {
      try {
        val doubleValue = value.toDouble()
        when {
          doubleValue.isInfinite() || doubleValue.isNaN() -> value
          doubleValue == doubleValue.toLong().toDouble() -> doubleValue.toLong()
          else -> doubleValue
        }
      } catch (e: NumberFormatException) {
        value
      }
    }

    else -> {
      try {
        val longValue = value.toLong()
        when {
          longValue in Int.MIN_VALUE..Int.MAX_VALUE -> longValue.toInt()
          else -> longValue
        }
      } catch (e: NumberFormatException) {
        try {
          BigInteger(value)
        } catch (e: NumberFormatException) {
          value
        }
      }
    }
  }

  private fun tryParseDate(value: String): Date? = try {
    isoDateFormat.parse(value)
  } catch (e: Exception) {
    null
  }

  private fun String.unquote(): String = if (length >= 2 && startsWith('"') && endsWith('"')) {
    substring(1, length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
  } else {
    this
  }
}
