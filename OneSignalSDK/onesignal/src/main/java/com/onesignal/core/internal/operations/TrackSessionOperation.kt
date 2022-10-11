package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.UserOperationExecutor

/**
 * An [Operation] to track the ending of a session, related to a specific user.
 */
internal class TrackSessionOperation() : Operation(UserOperationExecutor.TRACK_SESSION) {
    /**
     * The OneSignal appId the session was captured under.
     */
    var appId: String
        get() = getProperty(::appId.name)
        private set(value) { setProperty(::appId.name, value) }

    /**
     * The OneSignal ID driving the session. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        private set(value) { setProperty(::onesignalId.name, value) }

    /**
     * The amount of active time for the session, in milliseconds.
     */
    var sessionTime: Long
        get() = getProperty(::sessionTime.name)
        private set(value) { setProperty(::sessionTime.name, value) }

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, sessionTime: Long) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.sessionTime = sessionTime
    }
}
