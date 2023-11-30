package com.onesignal.user

import org.json.JSONObject

class UserChangedState(
    val previous: UserState,
    val current: UserState,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("previous", previous.toJSONObject())
            .put("current", current.toJSONObject())
    }
}
