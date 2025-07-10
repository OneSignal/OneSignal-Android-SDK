package com.onesignal.user.internal.customEvents.impl

import com.onesignal.user.internal.customEvents.ICustomEvent
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class CustomEvent(
    override val appId: String,
    override val name: String,
    override val properties: Map<String, Any>?,
    override val onesignalId: String?,
    override val externalId: String?,
    override val timeStamp: String,
    override val deviceType: String,
    override val sdk: String,
    override val appVersion: String,
) : ICustomEvent {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()

        json.put(NAME, name)
        // onesignal ID is not required if there exists an external ID
        if (externalId != null) {
            json.put(EXTERNAL_ID, externalId)
        } else {
            json.put(ONESIGNAL_ID, onesignalId)
        }
        json.put(TIMESTAMP, timeStamp.toString())

        // include some device properties under the event
        val ossdk = JSONObject()
        ossdk.put(DEVICE_TYPE, deviceType)
        ossdk.put(SDK, sdk)
        ossdk.put(APP_VERSION, appVersion)

        val payload = JSONObject()
        // add ossdk to the payload
        payload.put(PAYLOAD, ossdk)
        properties?.forEach { (key, value) ->
            payload.put(key, value)
        }
        json.put(PAYLOAD, payload)

        return JSONObject().put("events", JSONArray().put(json))
    }

    companion object {
        private const val NAME = "name"
        private const val ONESIGNAL_ID = "onesignal_id"
        private const val EXTERNAL_ID = "external_id"
        private const val PAYLOAD = "payload"
        private const val TIMESTAMP = "timestamp"
        private const val DEVICE_TYPE = "device_type"
        private const val SDK = "sdk"
        private const val APP_VERSION = "app_version"
    }
}
