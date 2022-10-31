package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor

/**
 * An [Operation] to delete a subscription from the OneSignal backend.
 */
class DeleteSubscriptionOperation() : Operation(SubscriptionOperationExecutor.DELETE_SUBSCRIPTION) {
    /**
     * The application ID this subscription that is to be deleted.
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
     * The subscription ID that is to be deleted. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    var subscriptionId: String
        get() = getProperty(::subscriptionId.name)
        private set(value) { setProperty(::subscriptionId.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Subscription.$subscriptionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId) && !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, subscriptionId: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
    }
}
