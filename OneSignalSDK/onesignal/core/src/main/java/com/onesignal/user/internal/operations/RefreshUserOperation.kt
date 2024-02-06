package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor

/**
 * An [Operation] to retrieve a user from the OneSignal backend. The resulting user
 * will replace the current user.
 */
class RefreshUserOperation() : Operation(RefreshUserOperationExecutor.REFRESH_USER) {
    /**
     * The application ID this subscription will be created under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId.Refresh"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Refresh"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.CREATE
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId)

    constructor(appId: String, onesignalId: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
    }
}
