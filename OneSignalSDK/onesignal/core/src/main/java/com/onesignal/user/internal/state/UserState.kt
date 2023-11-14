package com.onesignal.user.internal.state

import org.json.JSONObject

/**
 * A user state.
 */
class UserState (
        /**
         * The unique identifier for OneSignal account. This will be an empty string
         * until the user has been successfully logged in on the backend and
         * assigned an ID.  Use [addObserver] to be notified when the [onesignalId] has
         * been successfully assigned.
         */
        val onesignalId: String?,

        /**
         *  ????? What should be a good comment here ?????
         */
        val externalId: String?,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
                .put("onesignalId", onesignalId)
                .put("externalId", externalId)
    }
}