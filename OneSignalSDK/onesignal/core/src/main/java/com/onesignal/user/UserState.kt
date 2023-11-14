package com.onesignal.user

import com.onesignal.common.IDManager
import org.json.JSONObject

/**
 * A user state.
 */
class UserState {
    /**
     * The unique identifier for your OneSignal account. This will be a null string until the
     * user has been successfully logged in on the backend and assigned an ID.
     * Use [addObserver] to be notified when the [onesignalId] has been successfully assigned.
     */
    val onesignalId: String?

    /**
     * The external identifier that you use to identify users. This will be an null string
     * until the user has been successfully logged in on the backend and
     * assigned an ID.  Use [addObserver] to be notified when the [externalId] has
     * been successfully assigned.
     */
    val externalId: String?

    constructor(onesignalId: String?, externalId: String?) {
        if (onesignalId == null) {
            this.onesignalId = null
        } else {
            this.onesignalId = IDManager.getNonLocalIDOrNull(onesignalId)
        }
        this.externalId = externalId
    }

    fun toJSONObject(): JSONObject {
        return JSONObject()
            .put("onesignalId", onesignalId)
            .put("externalId", externalId)
    }
}
