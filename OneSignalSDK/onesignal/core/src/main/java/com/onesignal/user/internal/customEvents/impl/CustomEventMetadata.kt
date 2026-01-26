package com.onesignal.user.internal.customEvents.impl

import com.onesignal.common.putSafe
import org.json.JSONException
import org.json.JSONObject

class CustomEventMetadata(
    val deviceType: String?,
    val sdk: String?,
    val appVersion: String?,
    val type: String?,
    val deviceModel: String?,
    val deviceOS: String?,
) {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.putSafe(SDK, sdk)
        json.putSafe(APP_VERSION, appVersion)
        json.putSafe(TYPE, type)
        json.putSafe(DEVICE_TYPE, deviceType)
        json.putSafe(DEVICE_MODEL, deviceModel)
        json.putSafe(DEVICE_OS, deviceOS)
        return json
    }

    override fun toString(): String {
        return toJSONObject().toString()
    }

    companion object {
        private const val DEVICE_TYPE = "device_type"
        private const val SDK = "sdk"
        private const val APP_VERSION = "app_version"
        private const val TYPE = "type"
        private const val DEVICE_MODEL = "device_model"
        private const val DEVICE_OS = "device_os"
    }
}
