package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor

/**
 * An [Operation] to delete an alias from the OneSignal backend.
 */
class DeleteAliasOperation() : Operation(IdentityOperationExecutor.DELETE_ALIAS) {
    /**
     * The application ID this alias that is to be deleted.
     */
    var appId: String
        get() = getProperty(::appId.name)
        private set(value) { setProperty(::appId.name, value) }

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        private set(value) { setProperty(::onesignalId.name, value) }

    /**
     * The alias label to be deleted.
     */
    var label: String
        get() = getProperty(::label.name)
        private set(value) { setProperty(::label.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Alias.$label"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, label: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.label = label
    }
}
