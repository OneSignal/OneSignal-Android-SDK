package com.onesignal.user.internal.customEvents.impl

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.time.ITime
import com.onesignal.user.internal.customEvents.ICustomEventController
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.TrackCustomEventOperation
import org.json.JSONArray
import org.json.JSONObject

class CustomEventController(
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
    private val _opRepo: IOperationRepo,
) : ICustomEventController {
    override fun sendCustomEvent(
        name: String,
        properties: Map<String, Any>?,
    ) {
        val op =
            TrackCustomEventOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                _time.currentTimeMillis,
                name,
                properties?.let { mapToJson(it).toString() },
            )
        _opRepo.enqueue(op)
    }

    /**
     * Recursively convert a JSON-serializable map into a JSON-compatible format, handling
     * nested Maps and Lists appropriately.
     */
    private fun mapToJson(map: Map<String, Any>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            json.put(key, convertToJson(value))
        }
        return json
    }

    private fun convertToJson(value: Any): Any {
        return when (value) {
            is Map<*, *> -> {
                val subMap =
                    value.entries
                        .filter { it.key is String }
                        .associate {
                            it.key as String to convertToJson(it.value!!)
                        }
                mapToJson(subMap)
            }
            is List<*> -> {
                val array = JSONArray()
                value.forEach { array.put(convertToJson(it!!)) }
                array
            }
            else -> value
        }
    }
}
