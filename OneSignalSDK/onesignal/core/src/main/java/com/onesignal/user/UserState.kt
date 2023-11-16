package com.onesignal.user

import org.json.JSONObject

/**
 * A user state.
 */
class UserState (
        /**
         * The unique identifier for OneSignal account. This will be a null string until
         * the user has been successfully logged in on the backend and assigned an ID.
         * Use [addObserver] to be notified when the [onesignalId] has been successfully
         * assigned.
         */
        val onesignalId: String?,

        /**
         * The unique external identifier. This will be a null string until the id has
         * been successfully assigned and retrieved from the server.
         * Use [addObserver] to be notified when the [externalId] has been successfully
         * assigned.
         */
        val externalId: String?,
) {
    fun toJSONObject(): JSONObject {
        return JSONObject()
                .put("onesignalId", onesignalId)
                .put("externalId", externalId)
    }
}