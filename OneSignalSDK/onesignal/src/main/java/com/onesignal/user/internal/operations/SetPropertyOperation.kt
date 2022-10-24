package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.UserOperationExecutor

/**
 * An [Operation] to update a property related to a specific user.
 */
class SetPropertyOperation() : Operation(UserOperationExecutor.SET_PROPERTY) {
    /**
     * The OneSignal appId the purchase was captured under.
     */
    var appId: String
        get() = getProperty(::appId.name)
        private set(value) { setProperty(::appId.name, value) }

    /**
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        private set(value) { setProperty(::onesignalId.name, value) }

    /**
     * The property that is to be updated against the user.
     */
    var property: String
        get() = getProperty(::property.name)
        private set(value) { setProperty(::property.name, value) }

    /**
     * The value of that property to update it to.
     */
    var value: Any?
        get() = getProperty(::value.name)
        private set(value) { setProperty(::value.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = createComparisonKey
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, property: String, value: Any?) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.property = property
        this.value = value
    }
}
