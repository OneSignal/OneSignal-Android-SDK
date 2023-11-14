package com.onesignal.user.internal.state

import org.json.JSONObject

class UserChangedState(
        val previous: UserState,
        val current: UserState,
        val switchedUsers: Boolean,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
                .put("previous", previous.toJSONObject())
                .put("current", current.toJSONObject())
                .put("switchedUsers", switchedUsers)
    }
}