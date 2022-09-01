package com.onesignal.onesignal.core.internal.outcomes

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class OutcomeSourceBody @JvmOverloads constructor(var notificationIds: JSONArray? = JSONArray(), var inAppMessagesIds: JSONArray? = JSONArray()) {

    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject = JSONObject()
            .put(OutcomeConstants.NOTIFICATION_IDS, notificationIds)
            .put(OutcomeConstants.IAM_IDS, inAppMessagesIds)

    override fun toString(): String {
        return "OSOutcomeSourceBody{" +
                "notificationIds=" + notificationIds +
                ", inAppMessagesIds=" + inAppMessagesIds +
                '}'
    }
}