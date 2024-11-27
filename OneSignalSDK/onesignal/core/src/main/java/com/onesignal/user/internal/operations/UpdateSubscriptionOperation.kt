package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

/**
 * An [Operation] to update an existing subscription in the OneSignal backend.
 */
class UpdateSubscriptionOperation() : Operation(SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION) {
    /**
     * The application ID this subscription that is to be deleted.
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
     * The subscription ID that is to be deleted. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        private set(value) {
            setStringProperty(::subscriptionId.name, value)
        }

    /**
     * The type of subscription.
     */
    var type: SubscriptionType
        get() = getEnumProperty(::type.name)
        private set(value) {
            setEnumProperty(::type.name, value)
        }

    /**
     * Whether this subscription is currently enabled.
     */
    var enabled: Boolean
        get() = getBooleanProperty(::enabled.name)
        private set(value) {
            setBooleanProperty(::enabled.name, value)
        }

    /**
     * The address-specific information for this subscription. Its contents depends on the type
     * of subscription:
     *
     * * [SubscriptionType.EMAIL]: An email address.
     * * [SubscriptionType.SMS]: A phone number in E.164 format.
     * * [SubscriptionType.PUSH]: The push token.
     */
    var address: String
        get() = getStringProperty(::address.name)
        private set(value) {
            setStringProperty(::address.name, value)
        }

    /**
     * The status of this subscription.
     */
    var status: SubscriptionStatus
        get() = getEnumProperty(::status.name)
        private set(value) {
            setEnumProperty(::status.name, value)
        }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Subscription.$subscriptionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)
    override val applyToRecordId: String get() = subscriptionId

    constructor(appId: String, onesignalId: String, subscriptionId: String, type: SubscriptionType, enabled: Boolean, address: String, status: SubscriptionStatus) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.type = type
        this.enabled = enabled
        this.address = address
        this.status = status
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
        if (map.containsKey(subscriptionId)) {
            subscriptionId = map[subscriptionId]!!
        }
    }
}
