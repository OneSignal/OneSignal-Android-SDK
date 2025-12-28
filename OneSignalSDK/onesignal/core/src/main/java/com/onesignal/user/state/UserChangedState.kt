package com.onesignal.user.state

import org.json.JSONObject

/**
 * Represents a change in the user state. This is provided to [IUserStateObserver.onUserStateChange]
 * when the user state has changed, typically when a user has logged in or out.
 */
class UserChangedState(
    /**
     * The current user state after the change.
     */
    val current: UserState,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("current", current.toJSONObject())
    }
}
