package com.onesignal.common

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
 * Expand into a [JSONObject] safely.
 *
 * @param name The name of the attribute that contains a [JSONObject] value.
 * @param into The lambda method that will be executed to explore the [JSONObject] value, if the
 * attribute exists.
 */
fun JSONObject.expand(name: String, into: (childObject: JSONObject) -> Unit) {
    if (this.has(name)) {
        into(this.getJSONObject(name))
    }
}
