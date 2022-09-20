package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to track session information related to a specific user.
 */
internal class TrackSessionOperation(
    /**
     * The OneSignal appId the purchase was captured under.
     */
    val appId: String,

    /**
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val onesignalId: String,

    /**
     * The amount of time since the start of the current session.
     */
    val sessionTime: Long,

    /**
     * The number of sessions that have been created.
     */
    val sessionCount: Int
) : Operation(UserOperationExecutor.TRACK_SESSION) {

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::sessionTime.name, sessionTime)
            .put(::sessionCount.name, sessionCount)
    }
}
