package com.ding1ding.jsbridge

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
    is Map<*, *> -> mapToJson(any)
    is Collection<*> -> collectionToJson(any)
    is Enum<*> -> JSONObject.quote(any.name)
    else -> try {
      objectToJson(any)
    } catch (e: Exception) {
      Logger.e(e) { "Failed to serialize object of type ${any::class.java.simpleName}" }
      JSONObject.quote(any.toString())
    }
  }

  private fun mapToJson(map: Map<*, *>): String {
    val jsonObject = JSONObject()
    for ((key, value) in map) {
      jsonObject.put(key.toString(), toJsonValue(value))
    }
    return jsonObject.toString()
  }

  private fun collectionToJson(collection: Collection<*>): String {
    val jsonArray = JSONArray()
    for (item in collection) {
      jsonArray.put(toJsonValue(item))
    }
    return jsonArray.toString()
  }

  private fun objectToJson(obj: Any): String {
    val jsonObject = JSONObject()
    obj::class.java.declaredFields
      .filter { !it.isSynthetic }
      .forEach { field ->
        field.isAccessible = true
        jsonObject.put(field.name, toJsonValue(field.get(obj)))
      }
    return jsonObject.toString()
  }

  private fun toJsonValue(value: Any?): Any? = when (value) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, is String, is Boolean, is Number, is Char -> value
    is Map<*, *> -> JSONObject(mapToJson(value))
    is Collection<*> -> JSONArray(collectionToJson(value))
    is Date -> isoDateFormat.format(value)
    is Enum<*> -> value.name
    else -> toJson(value)
  }

  fun fromJson(json: String): Any? = try {
    when {
      json == "null" -> null
      json.startsWith("{") && json.endsWith("}") -> parseJsonObject(json)
      json.startsWith("[") && json.endsWith("]") -> parseJsonArray(json)
      json.startsWith("\"") && json.endsWith("\"") -> {
        val unquoted = json.substring(1, json.length - 1)
        tryParseDate(unquoted) ?: unquoted
      }

      json == "true" -> true
      json == "false" -> false
      else -> parseNumber(json) ?: json
    }
  } catch (e: Exception) {
    Logger.e(e) { "Error parsing JSON: $json" }
    json // Return the original string if parsing fails
  }

  private fun parseJsonObject(json: String): Map<String, Any?> =
    JSONObject(json).keys().asSequence().associateWith { key ->
      fromJson(JSONObject(json).get(key).toString())
    }

  private fun parseJsonArray(json: String): List<Any?> = JSONArray(json).let { array ->
    (0 until array.length()).map { fromJson(array.get(it).toString()) }
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

  private fun parseNumber(value: String): Any? = when {
    value.contains(".") || value.lowercase(Locale.US).contains("e") -> {
      try {
        val doubleValue = value.toDouble()
        when {
          doubleValue.isInfinite() || doubleValue.isNaN() -> value
          doubleValue == doubleValue.toLong().toDouble() -> doubleValue.toLong()
          else -> doubleValue
        }
      } catch (e: NumberFormatException) {
        null
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
          null
        }
      }
    }
  }

  private fun tryParseDate(value: String): Date? = try {
    isoDateFormat.parse(value)
  } catch (e: Exception) {
    null
  }
}
