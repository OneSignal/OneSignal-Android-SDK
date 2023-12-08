package com.onesignal.user.state

import org.json.JSONObject

/**
 * A user state.
 */
class UserState(
    /**
     * The unique identifier for your OneSignal account. This will be an empty string until the
     * user has been successfully logged in on the backend and assigned an ID.
     * Use [addObserver] to be notified when the [onesignalId] has been successfully assigned.
     */
    val onesignalId: String,
    /**
     * The external identifier that you use to identify users. Use [addObserver] to be notified
     * when the [externalId] has been successfully assigned. This will be an empty string if no
     * external identifier has been assigned to the associated [onesignalId].
     */
    val externalId: String,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("onesignalId", onesignalId)
            .put("externalId", externalId)
    }
}
