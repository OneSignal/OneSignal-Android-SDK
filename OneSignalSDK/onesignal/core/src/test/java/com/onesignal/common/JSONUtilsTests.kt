package com.onesignal.common

import android.os.Bundle
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject

class JSONUtilsTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    context("wrapInJsonArray") {
        test("should wrap a JSONObject in a JSONArray") {
            // Given
            val jsonObject = JSONObject().apply {
                put("key", "value")
            }

            // When
            val result = JSONUtils.wrapInJsonArray(jsonObject)

            // Then
            result.length() shouldBe 1
            result.getJSONObject(0).getString("key") shouldBe "value"
        }

        test("should handle null JSONObject") {
            // Given
            val jsonObject: JSONObject? = null

            // When
            val result = JSONUtils.wrapInJsonArray(jsonObject)

            // Then
            result.length() shouldBe 1
            result.isNull(0) shouldBe true
        }

        test("should wrap empty JSONObject") {
            // Given
            val jsonObject = JSONObject()

            // When
            val result = JSONUtils.wrapInJsonArray(jsonObject)

            // Then
            result.length() shouldBe 1
            result.getJSONObject(0).length() shouldBe 0
        }
    }

    context("bundleAsJSONObject") {
        test("should convert Bundle to JSONObject") {
            // Given
            val bundle = mockk<Bundle>(relaxed = true)
            val keySet = setOf("stringKey", "intKey", "boolKey")
            every { bundle.keySet() } returns keySet
            every { bundle["stringKey"] } returns "stringValue"
            every { bundle["intKey"] } returns 42
            every { bundle["boolKey"] } returns true

            // When
            val result = JSONUtils.bundleAsJSONObject(bundle)

            // Then
            result.getString("stringKey") shouldBe "stringValue"
            result.getInt("intKey") shouldBe 42
            result.getBoolean("boolKey") shouldBe true
        }

        test("should handle empty Bundle") {
            // Given
            val bundle = mockk<Bundle>(relaxed = true)
            every { bundle.keySet() } returns emptySet()

            // When
            val result = JSONUtils.bundleAsJSONObject(bundle)

            // Then
            result.length() shouldBe 0
        }

        test("should handle Bundle with null values") {
            // Given
            val bundle = mockk<Bundle>(relaxed = true)
            val keySet = setOf("key1", "key2")
            every { bundle.keySet() } returns keySet
            every { bundle["key1"] } returns "value1"
            every { bundle["key2"] } returns null

            // When
            val result = JSONUtils.bundleAsJSONObject(bundle)

            // Then
            result.getString("key1") shouldBe "value1"
            result.isNull("key2") shouldBe true
        }
    }

    context("jsonStringToBundle") {
        test("should return null for invalid JSON string") {
            // Given
            val invalidJson = "{invalid json}"

            // When
            val result = JSONUtils.jsonStringToBundle(invalidJson)

            // Then
            result shouldBe null
        }

        test("should return null for empty string") {
            // Given
            val emptyString = ""

            // When
            val result = JSONUtils.jsonStringToBundle(emptyString)

            // Then
            result shouldBe null
        }
    }

    context("newStringMapFromJSONObject") {
        test("should convert JSONObject to Map") {
            // Given
            val jsonObject = JSONObject().apply {
                put("key1", "value1")
                put("key2", "value2")
                put("key3", 123)
            }

            // When
            val result = JSONUtils.newStringMapFromJSONObject(jsonObject)

            // Then
            result.size shouldBe 3
            result["key1"] shouldBe "value1"
            result["key2"] shouldBe "value2"
            result["key3"] shouldBe "123"
        }

        test("should handle null values as empty string") {
            // Given
            val jsonObject = JSONObject().apply {
                put("key1", "value1")
                put("key2", JSONObject.NULL)
            }

            // When
            val result = JSONUtils.newStringMapFromJSONObject(jsonObject)

            // Then
            result["key1"] shouldBe "value1"
            result["key2"] shouldBe ""
        }

        test("should omit nested JSONObjects") {
            // Given
            val jsonObject = JSONObject().apply {
                put("key1", "value1")
                put("nested", JSONObject().apply { put("inner", "value") })
            }

            // When
            val result = JSONUtils.newStringMapFromJSONObject(jsonObject)

            // Then
            result.size shouldBe 1
            result["key1"] shouldBe "value1"
            result.containsKey("nested") shouldBe false
        }

        test("should omit JSONArrays") {
            // Given
            val jsonObject = JSONObject().apply {
                put("key1", "value1")
                put("array", JSONArray().put("item1").put("item2"))
            }

            // When
            val result = JSONUtils.newStringMapFromJSONObject(jsonObject)

            // Then
            result.size shouldBe 1
            result["key1"] shouldBe "value1"
            result.containsKey("array") shouldBe false
        }

        test("should handle empty JSONObject") {
            // Given
            val jsonObject = JSONObject()

            // When
            val result = JSONUtils.newStringMapFromJSONObject(jsonObject)

            // Then
            result.size shouldBe 0
        }
    }

    context("newStringSetFromJSONArray") {
        test("should convert JSONArray to Set") {
            // Given
            val jsonArray = JSONArray().apply {
                put("item1")
                put("item2")
                put("item3")
            }

            // When
            val result = JSONUtils.newStringSetFromJSONArray(jsonArray)

            // Then
            result.size shouldBe 3
            result shouldBe setOf("item1", "item2", "item3")
        }

        test("should handle empty JSONArray") {
            // Given
            val jsonArray = JSONArray()

            // When
            val result = JSONUtils.newStringSetFromJSONArray(jsonArray)

            // Then
            result.size shouldBe 0
        }

        test("should handle JSONArray with duplicate values") {
            // Given
            val jsonArray = JSONArray().apply {
                put("item1")
                put("item2")
                put("item1")
            }

            // When
            val result = JSONUtils.newStringSetFromJSONArray(jsonArray)

            // Then
            result.size shouldBe 2
            result shouldBe setOf("item1", "item2")
        }
    }

    context("toUnescapedEUIDString") {
        test("should unescape forward slashes in external_user_id") {
            // Given
            val jsonObject = JSONObject().apply {
                put("external_user_id", "user/123")
                put("other_key", "value")
            }

            // When
            val result = JSONUtils.toUnescapedEUIDString(jsonObject)

            // Then
            result shouldContain "\"external_user_id\":\"user/123\""
            result shouldNotContain "user\\/123"
        }

        test("should handle JSON without external_user_id") {
            // Given
            val jsonObject = JSONObject().apply {
                put("key1", "value1")
                put("key2", "value2")
            }

            // When
            val result = JSONUtils.toUnescapedEUIDString(jsonObject)

            // Then
            result shouldContain "key1"
            result shouldContain "value1"
        }

        test("should handle external_user_id without slashes") {
            // Given
            val jsonObject = JSONObject().apply {
                put("external_user_id", "user123")
            }

            // When
            val result = JSONUtils.toUnescapedEUIDString(jsonObject)

            // Then
            result shouldContain "\"external_user_id\":\"user123\""
        }

        test("should handle multiple escaped slashes") {
            // Given
            val jsonObject = JSONObject().apply {
                put("external_user_id", "user/123/456")
            }

            // When
            val result = JSONUtils.toUnescapedEUIDString(jsonObject)

            // Then
            result shouldContain "\"external_user_id\":\"user/123/456\""
            result shouldNotContain "\\/"
        }

        test("should handle empty external_user_id") {
            // Given
            val jsonObject = JSONObject().apply {
                put("external_user_id", "")
            }

            // When
            val result = JSONUtils.toUnescapedEUIDString(jsonObject)

            // Then
            result shouldContain "\"external_user_id\":\"\""
        }
    }

    context("compareJSONArrays") {
        test("should return true for equal JSONArrays") {
            // Given
            val array1 = JSONArray().apply {
                put("item1")
                put("item2")
            }
            val array2 = JSONArray().apply {
                put("item1")
                put("item2")
            }

            // When
            val result = JSONUtils.compareJSONArrays(array1, array2)

            // Then
            result shouldBe true
        }

        test("should return true for both null arrays") {
            // When
            val result = JSONUtils.compareJSONArrays(null, null)

            // Then
            result shouldBe true
        }

        test("should return false when one array is null") {
            // Given
            val array1 = JSONArray().put("item1")

            // When
            val result1 = JSONUtils.compareJSONArrays(array1, null)
            val result2 = JSONUtils.compareJSONArrays(null, array1)

            // Then
            result1 shouldBe false
            result2 shouldBe false
        }

        test("should return false for arrays of different sizes") {
            // Given
            val array1 = JSONArray().apply {
                put("item1")
                put("item2")
            }
            val array2 = JSONArray().put("item1")

            // When
            val result = JSONUtils.compareJSONArrays(array1, array2)

            // Then
            result shouldBe false
        }

        test("should return false for arrays with different items") {
            // Given
            val array1 = JSONArray().apply {
                put("item1")
                put("item2")
            }
            val array2 = JSONArray().apply {
                put("item1")
                put("item3")
            }

            // When
            val result = JSONUtils.compareJSONArrays(array1, array2)

            // Then
            result shouldBe false
        }

        test("should handle arrays with different order but same items") {
            // Given
            val array1 = JSONArray().apply {
                put("item1")
                put("item2")
            }
            val array2 = JSONArray().apply {
                put("item2")
                put("item1")
            }

            // When
            val result = JSONUtils.compareJSONArrays(array1, array2)

            // Then
            result shouldBe true
        }

        test("should handle arrays with numbers") {
            // Given
            val array1 = JSONArray().apply {
                put(1)
                put(2)
            }
            val array2 = JSONArray().apply {
                put(1)
                put(2)
            }

            // When
            val result = JSONUtils.compareJSONArrays(array1, array2)

            // Then
            result shouldBe true
        }
    }

    context("normalizeType") {
        test("should convert Int to Long") {
            // Given
            val intValue = 42

            // When
            val result = JSONUtils.normalizeType(intValue)

            // Then
            result shouldBe 42L
        }

        test("should convert Float to Double") {
            // Given
            val floatValue = 3.14f

            // When
            val result = JSONUtils.normalizeType(floatValue)

            // Then
            // Float to Double conversion has precision differences, so use approximate comparison
            result shouldNotBe null
            val doubleValue = result as? Number
            doubleValue shouldNotBe null
            val difference = kotlin.math.abs(doubleValue!!.toDouble() - 3.14)
            (difference < 0.0001) shouldBe true
        }

        test("should return other types unchanged") {
            // Given
            val stringValue = "test"
            val boolValue = true
            val longValue = 100L

            // When
            val stringResult = JSONUtils.normalizeType(stringValue)
            val boolResult = JSONUtils.normalizeType(boolValue)
            val longResult = JSONUtils.normalizeType(longValue)

            // Then
            stringResult shouldBe "test"
            boolResult shouldBe true
            longResult shouldBe 100L
        }
    }

    context("isValidJsonObject") {
        test("should return true for primitive types") {
            // Then
            JSONUtils.isValidJsonObject(null) shouldBe true
            JSONUtils.isValidJsonObject(true) shouldBe true
            JSONUtils.isValidJsonObject(false) shouldBe true
            JSONUtils.isValidJsonObject(42) shouldBe true
            JSONUtils.isValidJsonObject(3.14) shouldBe true
            JSONUtils.isValidJsonObject("string") shouldBe true
        }

        test("should return true for JSONObject and JSONArray") {
            // Given
            val jsonObject = JSONObject()
            val jsonArray = JSONArray()

            // Then
            JSONUtils.isValidJsonObject(jsonObject) shouldBe true
            JSONUtils.isValidJsonObject(jsonArray) shouldBe true
        }

        test("should return true for valid Map with String keys") {
            // Given
            val map = mapOf(
                "key1" to "value1",
                "key2" to 42,
                "key3" to true,
            )

            // When
            val result = JSONUtils.isValidJsonObject(map)

            // Then
            result shouldBe true
        }

        test("should return false for Map with non-String keys") {
            // Given
            val map = mapOf(
                1 to "value1",
                2 to "value2",
            )

            // When
            val result = JSONUtils.isValidJsonObject(map)

            // Then
            result shouldBe false
        }

        test("should return true for valid List") {
            // Given
            val list = listOf("item1", "item2", 42, true)

            // When
            val result = JSONUtils.isValidJsonObject(list)

            // Then
            result shouldBe true
        }

        test("should return true for nested valid structures") {
            // Given
            val nestedMap = mapOf(
                "key1" to "value1",
                "key2" to mapOf(
                    "nestedKey" to "nestedValue",
                ),
                "key3" to listOf("item1", "item2"),
            )

            // When
            val result = JSONUtils.isValidJsonObject(nestedMap)

            // Then
            result shouldBe true
        }

        test("should return false for nested invalid structures") {
            // Given
            val invalidMap = mapOf(
                "key1" to "value1",
                "key2" to mapOf(
                    1 to "invalid", // non-String key
                ),
            )

            // When
            val result = JSONUtils.isValidJsonObject(invalidMap)

            // Then
            result shouldBe false
        }

        test("should return false for non-JSON types") {
            // Then
            JSONUtils.isValidJsonObject(Any()) shouldBe false
            JSONUtils.isValidJsonObject(Exception()) shouldBe false
            JSONUtils.isValidJsonObject(Thread.currentThread()) shouldBe false
        }

        test("should return true for List containing valid nested structures") {
            // Given
            val list = listOf(
                "string",
                42,
                mapOf("key" to "value"),
                listOf("nested", "items"),
            )

            // When
            val result = JSONUtils.isValidJsonObject(list)

            // Then
            result shouldBe true
        }

        test("should return false for List containing invalid types") {
            // Given
            val list = listOf(
                "string",
                Any(), // invalid type
            )

            // When
            val result = JSONUtils.isValidJsonObject(list)

            // Then
            result shouldBe false
        }
    }

    context("mapToJson") {
        test("should convert simple map to JSONObject") {
            // Given
            val map = mapOf(
                "key1" to "value1",
                "key2" to 42,
                "key3" to true,
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            result.getString("key1") shouldBe "value1"
            result.getInt("key2") shouldBe 42
            result.getBoolean("key3") shouldBe true
        }

        test("should convert empty map to empty JSONObject") {
            // Given
            val map = emptyMap<String, Any>()

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            result.length() shouldBe 0
        }

        test("should convert map with nested map") {
            // Given
            val map = mapOf(
                "key1" to "value1",
                "nested" to mapOf(
                    "nestedKey1" to "nestedValue1",
                    "nestedKey2" to 100,
                ),
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            result.getString("key1") shouldBe "value1"
            val nested = result.getJSONObject("nested")
            nested.getString("nestedKey1") shouldBe "nestedValue1"
            nested.getInt("nestedKey2") shouldBe 100
        }

        test("should convert map with list values") {
            // Given
            val map = mapOf(
                "key1" to listOf("item1", "item2", "item3"),
                "key2" to listOf(1, 2, 3),
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            val array1 = result.getJSONArray("key1")
            array1.length() shouldBe 3
            array1.getString(0) shouldBe "item1"
            array1.getString(1) shouldBe "item2"
            array1.getString(2) shouldBe "item3"

            val array2 = result.getJSONArray("key2")
            array2.length() shouldBe 3
            array2.getInt(0) shouldBe 1
            array2.getInt(1) shouldBe 2
            array2.getInt(2) shouldBe 3
        }

        test("should convert map with deeply nested structures") {
            // Given
            val map = mapOf(
                "level1" to mapOf(
                    "level2" to mapOf(
                        "level3" to "deepValue",
                    ),
                ),
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            val level1 = result.getJSONObject("level1")
            val level2 = level1.getJSONObject("level2")
            level2.getString("level3") shouldBe "deepValue"
        }

        test("should convert map with list containing maps") {
            // Given
            val map = mapOf(
                "items" to listOf(
                    mapOf("name" to "item1", "value" to 10),
                    mapOf("name" to "item2", "value" to 20),
                ),
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            val array = result.getJSONArray("items")
            array.length() shouldBe 2
            val item1 = array.getJSONObject(0)
            item1.getString("name") shouldBe "item1"
            item1.getInt("value") shouldBe 10
            val item2 = array.getJSONObject(1)
            item2.getString("name") shouldBe "item2"
            item2.getInt("value") shouldBe 20
        }

        test("should handle null values") {
            // Given
            val map = mapOf(
                "key1" to "value1",
                "key2" to JSONObject.NULL,
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            result.getString("key1") shouldBe "value1"
            result.isNull("key2") shouldBe true
        }

        test("should handle different number types") {
            // Given
            val map = mapOf(
                "int" to 42,
                "long" to 100L,
                "double" to 3.14,
                "float" to 2.5f,
            )

            // When
            val result = JSONUtils.mapToJson(map)

            // Then
            result.getInt("int") shouldBe 42
            result.getLong("long") shouldBe 100L
            result.getDouble("double") shouldBe 3.14
            // Float precision may differ, so check approximately
            (kotlin.math.abs(result.getDouble("float") - 2.5) < 0.0001) shouldBe true
        }
    }

    context("convertToJson") {
        test("should return primitive values unchanged") {
            // Then
            JSONUtils.convertToJson("string") shouldBe "string"
            JSONUtils.convertToJson(42) shouldBe 42
            JSONUtils.convertToJson(true) shouldBe true
            JSONUtils.convertToJson(false) shouldBe false
            JSONUtils.convertToJson(3.14) shouldBe 3.14
        }

        test("should convert Map to JSONObject") {
            // Given
            val map = mapOf(
                "key1" to "value1",
                "key2" to 42,
            )

            // When
            val result = JSONUtils.convertToJson(map)

            // Then
            (result is JSONObject) shouldBe true
            val jsonObject = result as JSONObject
            jsonObject.getString("key1") shouldBe "value1"
            jsonObject.getInt("key2") shouldBe 42
        }

        test("should convert List to JSONArray") {
            // Given
            val list = listOf("item1", "item2", "item3")

            // When
            val result = JSONUtils.convertToJson(list)

            // Then
            (result is JSONArray) shouldBe true
            val jsonArray = result as JSONArray
            jsonArray.length() shouldBe 3
            jsonArray.getString(0) shouldBe "item1"
            jsonArray.getString(1) shouldBe "item2"
            jsonArray.getString(2) shouldBe "item3"
        }

        test("should convert nested Map recursively") {
            // Given
            val map = mapOf(
                "outer" to mapOf(
                    "inner" to "value",
                ),
            )

            // When
            val result = JSONUtils.convertToJson(map)

            // Then
            val jsonObject = result as JSONObject
            val inner = jsonObject.getJSONObject("outer")
            inner.getString("inner") shouldBe "value"
        }

        test("should convert List containing Maps") {
            // Given
            val list = listOf(
                mapOf("key1" to "value1"),
                mapOf("key2" to "value2"),
            )

            // When
            val result = JSONUtils.convertToJson(list)

            // Then
            val jsonArray = result as JSONArray
            jsonArray.length() shouldBe 2
            val item1 = jsonArray.getJSONObject(0)
            item1.getString("key1") shouldBe "value1"
            val item2 = jsonArray.getJSONObject(1)
            item2.getString("key2") shouldBe "value2"
        }

        test("should convert Map containing List") {
            // Given
            val map = mapOf(
                "items" to listOf("a", "b", "c"),
            )

            // When
            val result = JSONUtils.convertToJson(map)

            // Then
            val jsonObject = result as JSONObject
            val array = jsonObject.getJSONArray("items")
            array.length() shouldBe 3
            array.getString(0) shouldBe "a"
            array.getString(1) shouldBe "b"
            array.getString(2) shouldBe "c"
        }

        test("should convert empty List to empty JSONArray") {
            // Given
            val list = emptyList<Any>()

            // When
            val result = JSONUtils.convertToJson(list)

            // Then
            val jsonArray = result as JSONArray
            jsonArray.length() shouldBe 0
        }

        test("should convert empty Map to empty JSONObject") {
            // Given
            val map = emptyMap<String, Any>()

            // When
            val result = JSONUtils.convertToJson(map)

            // Then
            val jsonObject = result as JSONObject
            jsonObject.length() shouldBe 0
        }

        test("should handle List with mixed types") {
            // Given
            val list = listOf("string", 42, true, 3.14)

            // When
            val result = JSONUtils.convertToJson(list)

            // Then
            val jsonArray = result as JSONArray
            jsonArray.length() shouldBe 4
            jsonArray.getString(0) shouldBe "string"
            jsonArray.getInt(1) shouldBe 42
            jsonArray.getBoolean(2) shouldBe true
            jsonArray.getDouble(3) shouldBe 3.14
        }

        test("should filter out non-String keys from Map") {
            // Given
            val map = mapOf(
                "validKey" to "value1",
                123 to "value2", // non-String key should be filtered
            )

            // When
            val result = JSONUtils.convertToJson(map)

            // Then
            val jsonObject = result as JSONObject
            jsonObject.length() shouldBe 1
            jsonObject.getString("validKey") shouldBe "value1"
            jsonObject.has("123") shouldBe false
        }

        test("should handle deeply nested structures") {
            // Given
            val structure = mapOf(
                "level1" to listOf(
                    mapOf(
                        "level2" to listOf(
                            mapOf("level3" to "deepValue"),
                        ),
                    ),
                ),
            )

            // When
            val result = JSONUtils.convertToJson(structure)

            // Then
            val jsonObject = result as JSONObject
            val level1Array = jsonObject.getJSONArray("level1")
            val level1Item = level1Array.getJSONObject(0)
            val level2Array = level1Item.getJSONArray("level2")
            val level2Item = level2Array.getJSONObject(0)
            level2Item.getString("level3") shouldBe "deepValue"
        }
    }
})
