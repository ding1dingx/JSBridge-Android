package com.ding1ding.jsbridge

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object JsonUtils {
  fun toJson(any: Any?): String = when (any) {
    null -> "null"
    is JSONObject -> any.toString()
    is JSONArray -> any.toString()
    is String -> "\"$any\""
    is Boolean, is Number -> any.toString()
    is Map<*, *> -> JSONObject(any.mapValues { it.value.toString() }).toString()
    is Collection<*> -> JSONArray(any).toString()
    else -> JSONObject().put("value", any.toString()).toString()
  }

  fun fromJson(json: String): Any? = try {
    when {
      json == "null" -> null
      json.startsWith("{") && json.endsWith("}") -> jsonObjectToMap(JSONObject(json))
      json.startsWith("[") && json.endsWith("]") -> jsonArrayToList(JSONArray(json))
      json.startsWith("\"") && json.endsWith("\"") -> json.substring(1, json.length - 1)
      json == "true" -> true
      json == "false" -> false
      json.toIntOrNull() != null -> json.toInt()
      json.toLongOrNull() != null -> json.toLong()
      json.toDoubleOrNull() != null -> json.toDouble()
      else -> json
    }
  } catch (e: Exception) {
    Log.d("[JsBridge]", "Error parsing JSON: ${e.message}")
    null
  }

  private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any?> =
    jsonObject.keys().asSequence().associateWith { key ->
      when (val value = jsonObject.get(key)) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        else -> value
      }
    }

  private fun jsonArrayToList(jsonArray: JSONArray): List<Any?> =
    (0 until jsonArray.length()).map { index ->
      when (val value = jsonArray.get(index)) {
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> jsonArrayToList(value)
        else -> value
      }
    }
}
