package com.onesignal.user.internal.operations

import com.onesignal.OneSignal
import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor

/**
 * An [Operation] to transfer a subscription to a new owner on the OneSignal backend.
 */
class TransferSubscriptionOperation() : Operation(SubscriptionOperationExecutor.TRANSFER_SUBSCRIPTION) {
    /**
     * The application ID this subscription will be transferred under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The subscription ID that is to be transferred. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        private set(value) {
            setStringProperty(::subscriptionId.name, value)
        }

    /**
     * The user ID this subscription will be transferred to. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.Subscription.$subscriptionId.Transfer"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)
    override val applyToRecordId: String get() = subscriptionId

    constructor(appId: String, subscriptionId: String, onesignalId: String) : this() {
        this.appId = appId
        this.subscriptionId = subscriptionId
        this.onesignalId = onesignalId
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(subscriptionId)) {
            subscriptionId = map[subscriptionId]!!
        }

        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
    }
}
