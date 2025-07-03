package com.onesignal.user.internal.customEvents.impl

import com.onesignal.user.internal.customEvents.ICustomEvent
import org.json.JSONException
import org.json.JSONObject

class CustomEvent(
    override val name: String,
    override val properties: Map<String, CustomEventProperty>,
    override val onesignalId: String,
    override val externalId: String?,
    override val timeStamp: String
) : ICustomEvent {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        // TODO:
        return json
    }

    override fun toString(): String {
        return "{" +
            "name: " + name +
            ", properties=" + properties.toString()  +
            '}'
    }
}

