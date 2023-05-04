package com.onesignal.user.subscriptions

import org.json.JSONObject

class PushSubscriptionChangedState(
    val previous: PushSubscriptionState,
    val current: PushSubscriptionState
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
                .put("previous", previous.toJSONObject())
                .put("current", current.toJSONObject())
    }
}

