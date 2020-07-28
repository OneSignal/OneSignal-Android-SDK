package com.onesignal.outcomes.domain

import com.onesignal.outcomes.OSOutcomeConstants
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class OSOutcomeSourceBody @JvmOverloads constructor(var notificationIds: JSONArray? = JSONArray(), var inAppMessagesIds: JSONArray? = JSONArray()) {

    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject = JSONObject()
            .put(OSOutcomeConstants.NOTIFICATION_IDS, notificationIds)
            .put(OSOutcomeConstants.IAM_IDS, inAppMessagesIds)

    override fun toString(): String {
        return "OSOutcomeSourceBody{" +
                "notificationIds=" + notificationIds +
                ", inAppMessagesIds=" + inAppMessagesIds +
                '}'
    }
}