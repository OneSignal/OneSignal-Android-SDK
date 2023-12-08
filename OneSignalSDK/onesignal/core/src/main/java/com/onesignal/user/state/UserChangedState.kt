package com.onesignal.user.state

import org.json.JSONObject

class UserChangedState(
    val current: UserState,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("current", current.toJSONObject())
    }
}
