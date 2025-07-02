package com.onesignal.user.internal.customEvents.impl

import com.onesignal.user.internal.customEvents.ICustomEvent
import org.json.JSONArray
import org.json.JSONObject

class CustomEvent(
    override val name: String,
    override val properties: Map<String, Any>?,
) : ICustomEvent {
    val propertiesJson: JSONObject
        get() = properties?.let { mapToJson(it) } ?: JSONObject()

    private fun mapToJson(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, convertToJson(value))
        }
        return json
    }

    private fun convertToJson(value: Any?): Any? {
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
