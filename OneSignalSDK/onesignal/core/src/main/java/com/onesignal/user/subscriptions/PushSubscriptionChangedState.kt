package com.onesignal.user.subscriptions

import org.json.JSONObject

/**
 * Represents a change in the push subscription state. This is provided to
 * [IPushSubscriptionObserver.onPushSubscriptionChange] when the subscription has changed,
 * either because of a change driven by the application or by the backend.
 */
class PushSubscriptionChangedState(
    /**
     * The push subscription state before the change occurred.
     */
    val previous: PushSubscriptionState,
    /**
     * The current push subscription state after the change.
     */
    val current: PushSubscriptionState,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("previous", previous.toJSONObject())
            .put("current", current.toJSONObject())
    }
}
