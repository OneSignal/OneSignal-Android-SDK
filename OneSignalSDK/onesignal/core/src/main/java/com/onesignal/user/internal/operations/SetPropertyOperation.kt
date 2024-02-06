package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor

/**
 * An [Operation] to update a property related to a specific user.
 */
class SetPropertyOperation() : Operation(UpdateUserOperationExecutor.SET_PROPERTY) {
    /**
     * The OneSignal appId the purchase was captured under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * The property that is to be updated against the user.
     */
    var property: String
        get() = getStringProperty(::property.name)
        private set(value) {
            setStringProperty(::property.name, value)
        }

    /**
     * The value of that property to update it to.
     */
    var value: Any?
        get() = getOptAnyProperty(::value.name)
        private set(value) {
            setOptAnyProperty(::value.name, value)
        }

    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = createComparisonKey
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId)

    constructor(appId: String, onesignalId: String, property: String, value: Any?) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.property = property
        this.value = value
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
    }
}
