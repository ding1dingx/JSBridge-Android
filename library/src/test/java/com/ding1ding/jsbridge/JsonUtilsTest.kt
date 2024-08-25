package com.ding1ding.jsbridge

import java.math.BigInteger
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JsonUtilsTest {

  @Before
  fun setup() {
  }

  @Test
  fun testToJsonPrimitives() {
    assertEquals("null", JsonUtils.toJson(null))
    assertEquals("\"hello\"", JsonUtils.toJson("hello"))
    assertEquals("true", JsonUtils.toJson(true))
    assertEquals("false", JsonUtils.toJson(false))
    assertEquals("42", JsonUtils.toJson(42))
    assertEquals("3.14", JsonUtils.toJson(3.14))
  }

  @Test
  fun testToJsonDate() {
    val date = Date(1609459200000) // 2021-01-01T00:00:00.000Z
    assertEquals("\"2021-01-01T00:00:00.000Z\"", JsonUtils.toJson(date))
  }

  @Test
  fun testToJsonMap() {
    val map = mapOf("key" to "value", "number" to 42)
    assertEquals("{\"key\":\"value\",\"number\":42}", JsonUtils.toJson(map))
  }

  @Test
  fun testToJsonCollection() {
    val list = listOf("a", 1, true)
    assertEquals("[\"a\",1,true]", JsonUtils.toJson(list))
  }

  enum class TestEnum { VALUE }

  @Test
  fun testToJsonEnum() {
    assertEquals("\"VALUE\"", JsonUtils.toJson(TestEnum.VALUE))
  }

  @Test
  fun testToJsonCustomObject() {
    data class TestObject(val name: String, val age: Int)

    val obj = TestObject("John", 30)
    assertEquals("{\"name\":\"John\",\"age\":30}", JsonUtils.toJson(obj))
  }

  @Test
  fun testFromJsonNull() {
    assertNull(JsonUtils.fromJson("null"))
  }

  @Test
  fun testFromJsonObject() {
    val json = "{\"key\":\"value\",\"number\":42}"
    val result = JsonUtils.fromJson(json) as Map<*, *>
    assertEquals("value", result["key"])
    assertEquals(42, result["number"])
  }

  @Test
  fun testFromJsonArray() {
    val json = "[\"a\",1,true]"
    val result = JsonUtils.fromJson(json) as List<*>
    assertEquals("a", result[0])
    assertEquals(1, result[1])
    assertEquals(true, result[2])
  }

  @Test
  fun testFromJsonString() {
    assertEquals("hello", JsonUtils.fromJson("\"hello\""))
  }

  @Test
  fun testFromJsonBoolean() {
    assertEquals(true, JsonUtils.fromJson("true"))
    assertEquals(false, JsonUtils.fromJson("false"))
  }

  @Test
  fun testFromJsonNumber() {
    assertEquals(42, JsonUtils.fromJson("42"))
    assertEquals(3.14, JsonUtils.fromJson("3.14"))
    assertEquals(Long.MAX_VALUE, JsonUtils.fromJson(Long.MAX_VALUE.toString()))
    assertEquals(BigInteger("9223372036854775808"), JsonUtils.fromJson("9223372036854775808"))
  }

  @Test
  fun testFromJsonDate() {
    val dateString = "\"2021-01-01T00:00:00.000Z\""
    val result = JsonUtils.fromJson(dateString) as Date
    assertEquals(1609459200000, result.time)
  }

  @Test
  fun testRoundTripConversion() {
    val original = mapOf(
      "string" to "value",
      "number" to 42,
      "boolean" to true,
      "null" to null,
      "array" to listOf(1, 2, 3),
      "object" to mapOf("nested" to "value"),
    )
    val json = JsonUtils.toJson(original)
    val result = JsonUtils.fromJson(json) as Map<*, *>

    assertEquals(original["string"], result["string"])
    assertEquals(original["number"], result["number"])
    assertEquals(original["boolean"], result["boolean"])
    assertNull(result["null"])
    assertEquals(original["array"], result["array"])
    assertEquals(
      (original["object"] as Map<*, *>)["nested"],
      (result["object"] as Map<*, *>)["nested"],
    )
  }

  @Test
  fun testParseInvalidJson() {
    val invalidJson = "invalid json"
    assertEquals(invalidJson, JsonUtils.fromJson(invalidJson))
  }
}
