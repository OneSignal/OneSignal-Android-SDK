package com.onesignal.common

import android.os.Bundle
import com.onesignal.debug.internal.logging.Logging
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Matcher
import java.util.regex.Pattern

object JSONUtils {
    const val EXTERNAL_USER_ID = "external_user_id"

    /**
     * Wrap the provided [JSONObject] as a [JSONArray] with
     * one entry (the object).
     *
     * @param jsonObject The object that is to be wrapped within an array.
     *
     * @return A [JSONArray] containing one entry, the [jsonObject] provided.
     */
    fun wrapInJsonArray(jsonObject: JSONObject?): JSONArray {
        return JSONArray().put(jsonObject)
    }

    fun bundleAsJSONObject(bundle: Bundle): JSONObject {
        val json = JSONObject()
        val keys = bundle.keySet()
        for (key in keys) {
            try {
                json.put(key, bundle[key])
            } catch (e: JSONException) {
                Logging.error("bundleAsJSONObject error for key: $key", e)
            }
        }
        return json
    }

    fun jsonStringToBundle(data: String): Bundle? {
        return try {
            val jsonObject = JSONObject(data)
            val bundle = Bundle()
            val iterator: Iterator<*> = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next() as String
                val value = jsonObject.getString(key)
                bundle.putString(key, value)
            }
            bundle
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    fun newStringMapFromJSONObject(jsonObject: JSONObject): Map<String, String> {
        val keys: Iterator<String> = jsonObject.keys()
        val result = mutableMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            try {
                val value = jsonObject.opt(key)
                if (value is JSONArray || value is JSONObject) {
                    Logging.error("Omitting key '$key'! sendTags DO NOT supported nested values!")
                } else if (jsonObject.isNull(key) || "" == value) {
                    result[key] = ""
                } else {
                    result[key] = value.toString()
                }
            } catch (t: Throwable) {
            }
        }

        return result
    }

    // Creates a new Set<String> from a Set String by converting and iterating a JSONArray
    fun newStringSetFromJSONArray(jsonArray: JSONArray): Set<String> {
        val stringSet: MutableSet<String> = mutableSetOf()
        for (i in 0 until jsonArray.length()) {
            stringSet.add(jsonArray.getString(i))
        }
        return stringSet
    }

    /**
     * Returns the JSONObject as a String with the external user ID unescaped.
     * Needed b/c the default JSONObject.toString() escapes (/) with (\/), which customers may not want.
     */
    fun toUnescapedEUIDString(json: JSONObject): String {
        var strJsonBody = json.toString()
        if (json.has(EXTERNAL_USER_ID)) {
            // find the value of the external user ID
            val eidPattern = Pattern.compile("(?<=\"external_user_id\":\").*?(?=\")")
            val eidMatcher = eidPattern.matcher(strJsonBody)
            if (eidMatcher.find()) {
                val matched = eidMatcher.group(0)
                if (matched != null) {
                    var unescapedEID: String? = matched.replace("\\/", "/")
                    // backslashes (\) and dollar signs ($) in the replacement string will be treated literally
                    unescapedEID = Matcher.quoteReplacement(unescapedEID)
                    strJsonBody = eidMatcher.replaceAll(unescapedEID)
                }
            }
        }
        return strJsonBody
    }

    /**
     * Compare two JSONArrays too determine if they are equal or not
     */
    fun compareJSONArrays(
        jsonArray1: JSONArray?,
        jsonArray2: JSONArray?,
    ): Boolean {
        // If both JSONArrays are null, they are equal
        if (jsonArray1 == null && jsonArray2 == null) return true

        // If one JSONArray is null but not the other, they are not equal
        if (jsonArray1 == null || jsonArray2 == null) return false

        // If one JSONArray is a different size then the other, they are not equal
        if (jsonArray1.length() != jsonArray2.length()) return false
        try {
            L1@ for (i in 0 until jsonArray1.length()) {
                for (j in 0 until jsonArray2.length()) {
                    val obj1 = normalizeType(jsonArray1[i])
                    val obj2 = normalizeType(jsonArray2[j])
                    // Make sure jsonArray1 current item exists somewhere inside jsonArray2
                    // If item found continue looping
                    if (obj1 == obj2) continue@L1
                }

                // Could not find current item from jsonArray1 inside jsonArray2, so they are not equal
                return false
            }
        } catch (e: JSONException) {
            e.printStackTrace()

            // Exception thrown, return false
            return false
        }

        // JSONArrays are equal
        return true
    }

    // Converts Java types that are equivalent in the JSON format to the same types.
    // This allows for assertEquals on two values from JSONObject.get to test values as long as it
    //   returns in the same JSON output.
    fun normalizeType(`object`: Any): Any? {
        val clazz: Class<*> = `object`.javaClass
        if (clazz == Int::class.java) {
            return (`object` as Int?)?.let { java.lang.Long.valueOf(it.toLong()) }
        }
        return if (clazz == Float::class.java) {
            (`object` as Float?)?.let {
                java.lang.Double.valueOf(
                    it.toDouble(),
                )
            }
        } else {
            `object`
        }
    }

    /**
     * Check if an object is JSON-serializable.
     * Recursively check each item if object is a map or a list.
     */
    fun isValidJsonObject(value: Any?): Boolean {
        return when (value) {
            null,
            is Boolean,
            is Number,
            is String,
            is JSONObject,
            is JSONArray,
            -> true
            is Map<*, *> -> value.keys.all { it is String } && value.values.all { isValidJsonObject(it) }
            is List<*> -> value.all { isValidJsonObject(it) }
            else -> false
        }
    }

    /**
     * Recursively convert a JSON-serializable map into a JSON-compatible format, handling
     * nested Maps and Lists appropriately.
     */
    fun mapToJson(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, convertToJson(value))
        }
        return json
    }

    /**
     * Recursively converts maps and lists into JSON-compatible objects, transforming maps with
     * String keys into JSON objects, lists into JSON arrays, and leaving primitive values unchanged to support safe JSON serialization.
     * Null values are converted to JSONObject.NULL to preserve them in the JSON structure.
     */
    fun convertToJson(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                val subMap =
                    value.entries
                        .filter { it.key is String }
                        .associate {
                            it.key as String to convertToJson(it.value)
                        }
                mapToJson(subMap)
            }
            is List<*> -> {
                val array = JSONArray()
                value.forEach { array.put(convertToJson(it)) }
                array
            }
            else -> value
        }
    }
}
