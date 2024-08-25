package com.ding1ding.jsbridge

import java.math.BigInteger
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

  // 测试基本数据类型的序列化
  @Test
  fun testToJsonPrimitives() {
    assertEquals("null", JsonUtils.toJson(null))
    assertEquals("\"hello\"", JsonUtils.toJson("hello"))
    assertEquals("true", JsonUtils.toJson(true))
    assertEquals("false", JsonUtils.toJson(false))
    assertEquals("42", JsonUtils.toJson(42))
    assertEquals("3.14", JsonUtils.toJson(3.14))
  }

  // 测试日期的序列化
  @Test
  fun testToJsonDate() {
    val date = Date(1609459200000) // 2021-01-01T00:00:00.000Z
    assertEquals("\"2021-01-01T00:00:00.000Z\"", JsonUtils.toJson(date))
  }

  // 测试复杂 Map 的序列化和反序列化
  @Test
  fun testToJsonMap() {
    val original = mapOf(
      "key" to "value",
      "number" to 42,
      "boolean" to true,
      "null" to null,
      "nested" to mapOf("inner" to "value"),
      "array" to listOf(1, 2, 3),
    )
    val json = JsonUtils.toJson(original)
    println("JSON: $json")

    // 将 JSON 字符串解析回对象
    val result = JsonUtils.fromJson(json) as? Map<*, *>
    assertNotNull("Parsed result should not be null", result)

    // 比较原始 Map 和解析后的 Map
    assertEquals(original.size, result?.size)
    assertEquals(original["key"], result?.get("key"))
    assertEquals(original["number"], result?.get("number"))
    assertEquals(original["boolean"], result?.get("boolean"))
    assertNull(result?.get("null"))

    val originalNested = original["nested"] as Map<*, *>
    val resultNested = result?.get("nested") as? Map<*, *>
    assertNotNull("Nested object should not be null", resultNested)
    assertEquals(originalNested["inner"], resultNested?.get("inner"))

    val originalArray = original["array"] as List<*>
    val resultArray = result?.get("array") as? List<*>
    assertNotNull("Array should not be null", resultArray)
    assertEquals(originalArray, resultArray)
  }

  // 测试集合的序列化
  @Test
  fun testToJsonCollection() {
    val list = listOf("a", 1, true)
    assertEquals("[\"a\",1,true]", JsonUtils.toJson(list))
  }

  enum class TestEnum { VALUE }

  // 测试枚举的序列化
  @Test
  fun testToJsonEnum() {
    assertEquals("\"VALUE\"", JsonUtils.toJson(TestEnum.VALUE))
  }

  // 测试自定义对象的序列化
  @Test
  fun testToJsonCustomObject() {
    data class TestObject(val name: String, val age: Int)

    val obj = TestObject("John", 30)
    assertEquals("{\"name\":\"John\",\"age\":30}", JsonUtils.toJson(obj))
  }

  // 测试 null 值的反序列化
  @Test
  fun testFromJsonNull() {
    assertNull(JsonUtils.fromJson("null"))
  }

  // 测试 JSON 对象的反序列化
  @Test
  fun testFromJsonObject() {
    val json = "{\"key\":\"value\",\"number\":42}"
    val result = JsonUtils.fromJson(json) as Map<*, *>
    assertEquals("value", result["key"])
    assertEquals(42, result["number"])
  }

  // 测试 JSON 数组的反序列化
  @Test
  fun testFromJsonArray() {
    val json = "[\"a\",1,true]"
    val result = JsonUtils.fromJson(json) as List<*>
    assertEquals("a", result[0])
    assertEquals(1, result[1])
    assertEquals(true, result[2])
  }

  // 测试字符串的反序列化
  @Test
  fun testFromJsonString() {
    assertEquals("hello", JsonUtils.fromJson("\"hello\""))
  }

  // 测试布尔值的反序列化
  @Test
  fun testFromJsonBoolean() {
    assertEquals(true, JsonUtils.fromJson("true"))
    assertEquals(false, JsonUtils.fromJson("false"))
  }

  // 测试数字的反序列化
  @Test
  fun testFromJsonNumber() {
    assertEquals(42, JsonUtils.fromJson("42"))
    assertEquals(3.14, JsonUtils.fromJson("3.14"))
    assertEquals(Long.MAX_VALUE, JsonUtils.fromJson(Long.MAX_VALUE.toString()))
    assertEquals(BigInteger("9223372036854775808"), JsonUtils.fromJson("9223372036854775808"))
  }

  // 测试日期的反序列化
  @Test
  fun testFromJsonDate() {
    val dateString = "\"2021-01-01T00:00:00.000Z\""
    val result = JsonUtils.fromJson(dateString) as Date
    assertEquals(1609459200000, result.time)
  }

  // 测试复杂对象的序列化和反序列化一致性
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
    println("JSON: $json")

    val result = JsonUtils.fromJson(json)
    println("Result: $result")

    // 检查 result 的类型
    assertTrue("Result should be a Map, but was ${result?.javaClass}", result is Map<*, *>)

    if (result is Map<*, *>) {
      assertEquals(original["string"], result["string"])
      assertEquals(original["number"], result["number"])
      assertEquals(original["boolean"], result["boolean"])
      assertNull(result["null"])

      val array = result["array"]
      assertTrue("Array should be a List, but was ${array?.javaClass}", array is List<*>)
      if (array is List<*>) {
        assertEquals(original["array"], array)
      }

      val nestedObject = result["object"]
      assertTrue(
        "Nested object should be a Map, but was ${nestedObject?.javaClass}",
        nestedObject is Map<*, *>,
      )
      if (nestedObject is Map<*, *>) {
        assertEquals((original["object"] as Map<*, *>)["nested"], nestedObject["nested"])
      }
    }
  }

  // 测试无效 JSON 的处理
  @Test
  fun testParseInvalidJson() {
    val invalidJson = "invalid json"
    assertEquals(invalidJson, JsonUtils.fromJson(invalidJson))
  }

  // 测试复杂嵌套对象的序列化和反序列化
  @Test
  fun testComplexNestedObject() {
    data class Inner(val value: String)
    data class Outer(val inner: Inner, val list: List<Int>)

    val complex = Outer(Inner("nested"), listOf(1, 2, 3))
    val json = JsonUtils.toJson(complex)
    val result = JsonUtils.fromJson(json) as? Map<*, *>

    assertNotNull(result)
    val innerMap = result?.get("inner") as? Map<*, *>
    assertNotNull(innerMap)
    assertEquals("nested", innerMap?.get("value"))
    assertEquals(listOf(1, 2, 3), result?.get("list"))
  }

  // 测试空集合和空 Map 的处理
  @Test
  fun testEmptyCollectionsAndMaps() {
    val empty = mapOf(
      "emptyList" to emptyList<Any>(),
      "emptyMap" to emptyMap<Any, Any>(),
    )
    val json = JsonUtils.toJson(empty)
    val result = JsonUtils.fromJson(json) as? Map<*, *>

    assertNotNull(result)
    assertTrue((result?.get("emptyList") as? List<*>)?.isEmpty() == true)
    assertTrue((result?.get("emptyMap") as? Map<*, *>)?.isEmpty() == true)
  }

  // 测试特殊字符的处理
  @Test
  fun testSpecialCharacters() {
    val special = "Hello\n\t\r\b\u000c\u0001World"
    val json = JsonUtils.toJson(special)
    println("JSON: $json")
    val result = JsonUtils.fromJson(json)

    assertEquals(special, result)
    assertEquals("\"Hello\\n\\t\\r\\b\\f\\u0001World\"", json)
  }

  // 测试超大数字的处理
  @Test
  fun testVeryLargeNumbers() {
    val veryLarge = "9223372036854775808" // Long.MAX_VALUE + 1
    val json = JsonUtils.toJson(BigInteger(veryLarge))
    val result = JsonUtils.fromJson(json)

    assertTrue(result is BigInteger)
    assertEquals(BigInteger(veryLarge), result)
  }

  // 测试浮点数精度
  @Test
  fun testFloatingPointPrecision() {
    val precise = 1.23456789012345
    val json = JsonUtils.toJson(precise)
    val result = JsonUtils.fromJson(json)

    assertTrue(result is Double)
    assertEquals(precise, result as Double, 1e-15)
  }

  // 测试 Unicode 字符的处理
  @Test
  fun testUnicodeCharacters() {
    val unicode = "Hello, 世界 ! 🌍 "
    val json = JsonUtils.toJson(unicode)
    val result = JsonUtils.fromJson(json)

    assertEquals(unicode, result)
  }

  // 测试集合中 null 值的处理
  @Test
  fun testNullValuesInCollections() {
    val withNulls = listOf(1, null, "three", null)
    val json = JsonUtils.toJson(withNulls)
    val result = JsonUtils.fromJson(json) as? List<*>

    assertNotNull(result)
    assertEquals(withNulls, result)
  }

  // 测试非字符串键的 Map 处理
  @Test
  fun testMapWithNonStringKeys() {
    val map = mapOf(
      1 to "one",
      2.5 to "two point five",
      true to "boolean",
    )
    val json = JsonUtils.toJson(map)
    val result = JsonUtils.fromJson(json) as? Map<*, *>

    assertNotNull(result)
    assertEquals("one", result?.get("1"))
    assertEquals("two point five", result?.get("2.5"))
    assertEquals("boolean", result?.get("true"))
  }
}
