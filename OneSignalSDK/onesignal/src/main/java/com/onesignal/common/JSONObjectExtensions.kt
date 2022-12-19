package com.onesignal.common

import org.json.JSONArray
import org.json.JSONObject

/**
 * Retrieve an [Int] from the [JSONObject] safely.
 *
 * @param name The name of the attribute that contains an [Int] value.
 *
 * @return The [Int] value if it exists, null otherwise.
 */
fun JSONObject.safeInt(name: String): Int? {
    if (this.has(name)) {
        return this.getInt(name)
    }

    return null
}

/**
 * Retrieve an [Long] from the [JSONObject] safely.
 *
 * @param name The name of the attribute that contains an [Int] value.
 *
 * @return The [Long] value if it exists, null otherwise.
 */
fun JSONObject.safeLong(name: String): Long? {
    if (this.has(name)) {
        return this.getLong(name)
    }

    return null
}

/**
 * Retrieve an [Double] from the [JSONObject] safely.
 *
 * @param name The name of the attribute that contains an [Int] value.
 *
 * @return The [Double] value if it exists, null otherwise.
 */
fun JSONObject.safeDouble(name: String): Double? {
    if (this.has(name)) {
        return this.getDouble(name)
    }

    return null
}

/**
 * Retrieve a [Boolean] from the [JSONObject] safely.
 *
 * @param name The name of the attribute that contains a [Boolean] value.
 *
 * @return The [Boolean] value if it exists, null otherwise.
 */
fun JSONObject.safeBool(name: String): Boolean? {
    if (this.has(name)) {
        return this.getBoolean(name)
    }

    return null
}

/**
 * Retrieve a [String] from the [JSONObject] safely.
 *
 * @param name The name of the attribute that contains a [String] value.
 *
 * @return The [String] value if it exists, null otherwise.
 */
fun JSONObject.safeString(name: String): String? {
    if (this.has(name)) {
        return this.getString(name)
    }

    return null
}

/**
 * Retrieve a [JSONObject] from the JSONObject safely.
 *
 * @param name The name of the attribute that contains a [JSONObject] value.
 *
 * @return The [JSONObject] value if it exists, null otherwise.
 */
fun JSONObject.safeJSONObject(name: String): JSONObject? {
    if (this.has(name)) {
        return this.getJSONObject(name)
    }

    return null
}

/**
 * Create a [Map] from the [JSONObject].
 */
fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()

    for (key in this.keys()) {
        map[key] = this[key]
    }

    return map
}

/**
 * Expand into a [JSONObject] safely.
 *
 * @param name The name of the attribute that contains a [JSONObject] value.
 * @param into The lambda method that will be executed to explore the [JSONObject] value, if the
 * attribute exists.
 */
fun JSONObject.expandJSONObject(name: String, into: (childObject: JSONObject) -> Unit) {
    if (this.has(name)) {
        into(this.getJSONObject(name))
    }
}

fun <T> JSONObject.expandJSONArray(name: String, into: (childObject: JSONObject) -> T?): List<T> {
    val listToRet = mutableListOf<T>()
    if (this.has(name)) {
        val jsonArray = this.getJSONArray(name)
        for (index in 0 until jsonArray.length()) {
            val itemJSONObject = jsonArray.getJSONObject(index)
            val item = into(itemJSONObject)
            if (item != null) {
                listToRet.add(item)
            }
        }
    }

    return listToRet
}

/**
 * Populate the [JSONObject] with the [Map] provided.
 *
 * @param map: The map that will contain the name/values.
 *
 * @return The [JSONObject] itself, to allow for chaining.
 */
fun JSONObject.putMap(map: Map<String, Any?>): JSONObject {
    for (identity in map) {
        this.put(identity.key, identity.value ?: JSONObject.NULL)
    }

    return this
}

/**
 * Populate the [JSONObject] as attribute [name] with the [Map] provided.
 *
 * @param name: The name of the attribute that will contain the [JSONObject] value.
 * @param map: The map that will contain the name/values.
 *
 * @return The [JSONObject] itself, to allow for chaining.
 */
fun JSONObject.putMap(name: String, map: Map<String, Any?>?): JSONObject {
    if (map != null) {
        this.putJSONObject(name) {
            it.putMap(map)
        }
    }

    return this
}

/**
 * Put the attribute named by [name] with a [JSONObject] value, the contents
 * of which are determined by the expand.
 *
 * @param name: The name of the attribute that will contain the [JSONObject] value.
 * @param expand: The lambda that will be called to populate the [JSONObject] value.
 *
 * @return The [JSONObject] itself, to allow for chaining.
 */
fun JSONObject.putJSONObject(name: String, expand: (item: JSONObject) -> Unit): JSONObject {
    val childJSONObject = JSONObject()
    expand(childJSONObject)

    this.put(name, childJSONObject)

    return this
}

/**
 * Put the attribute named by [name] with a [JSONArray] value, the contenxt of which
 * are deteremined by the input.
 *
 * @param name: The name of the attribute that will contain the [JSONArray] value.
 * @param list: The list of items that will be converted into the [JSONArray].
 * @param create: The lambda that will be called for each item in [list], expecting a [JSONObject] to be added to the array.
 */
fun <T> JSONObject.putJSONArray(name: String, list: List<T>?, create: (item: T) -> JSONObject?): JSONObject {
    if (list != null) {
        val jsonArray = JSONArray()
        list.forEach {
            val item = create(it)
            if (item != null) {
                jsonArray.put(item)
            }
        }
        this.put(name, jsonArray)
    }

    return this
}

/**
 * Put the name/value pair into the [JSONObject].  If the [value] provided is null,
 * nothing will be put into the [JSONObject].
 *
 * @param name The name of the attribute the [value] will be saved to.
 * @param value The value to put into the [JSONObject]. If not null, the attribute name will not be added.
 *
 * @return The [JSONObject] itself, to allow for chaining.
 */
fun JSONObject.putSafe(name: String, value: Any?): JSONObject {
    if (value != null) {
        this.put(name, value)
    }

    return this
}
