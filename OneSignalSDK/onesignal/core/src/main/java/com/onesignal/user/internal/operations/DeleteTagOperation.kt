package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor

/**
 * An [Operation] to delete a tag from the OneSignal backend. The tag wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
class DeleteTagOperation() : Operation(UpdateUserOperationExecutor.DELETE_TAG) {
    /**
     * The application ID this subscription will be created under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) { setStringProperty(::appId.name, value) }

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) { setStringProperty(::onesignalId.name, value) }

    /**
     * The tag key to delete.
     */
    var key: String
        get() = getStringProperty(::key.name)
        private set(value) { setStringProperty(::key.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = createComparisonKey
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId)

    constructor(appId: String, onesignalId: String, key: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.key = key
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
    }
}
