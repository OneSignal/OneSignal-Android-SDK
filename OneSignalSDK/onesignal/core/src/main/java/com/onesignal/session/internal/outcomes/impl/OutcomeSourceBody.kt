package com.onesignal.session.internal.outcomes.impl

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class OutcomeSourceBody
    @JvmOverloads
    constructor(var notificationIds: JSONArray? = JSONArray(), var inAppMessagesIds: JSONArray? = JSONArray()) {
        @Throws(JSONException::class)
        fun toJSONObject(): JSONObject =
            JSONObject()
                .put(OutcomeConstants.NOTIFICATION_IDS, notificationIds)
                .put(OutcomeConstants.IAM_IDS, inAppMessagesIds)

        override fun toString(): String {
            return "OutcomeSourceBody{" +
                "notificationIds=" + notificationIds +
                ", inAppMessagesIds=" + inAppMessagesIds +
                '}'
        }
    }
