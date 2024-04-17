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

    /**
     * The alias label to be deleted.
     */
    var label: String
        get() = getStringProperty(::label.name)
        private set(value) {
            setStringProperty(::label.name, value)
        }

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Alias.$label"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId)
    override val applyToRecordId: String get() = onesignalId

    constructor(appId: String, onesignalId: String, label: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.label = label
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
    }
}
