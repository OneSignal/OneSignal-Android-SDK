package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.impl.SubscriptionOperationExecutor

/**
 * An [Operation] to update an existing subscription in the OneSignal backend.
 */
internal class UpdateSubscriptionOperation() : Operation(SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION) {
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

    /**
     * Whether this subscription is currently enabled.
     */
    var enabled: Boolean
        get() = getProperty(::enabled.name)
        private set(value) { setProperty(::enabled.name, value) }

    /**
     * The address-specific information for this subscription. Its contents depends on the type
     * of subscription:
     *
     * * [SubscriptionType.EMAIL]: An email address.
     * * [SubscriptionType.SMS]: A phone number in E.164 format.
     * * [SubscriptionType.PUSH]: The push token.
     */
    var address: String
        get() = getProperty(::address.name)
        private set(value) { setProperty(::address.name, value) }

    /**
     * The status of this subscription.
     */
    var status: Int
        get() = getProperty(::status.name)
        private set(value) { setProperty(::status.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Subscription.$subscriptionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId) && !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, subscriptionId: String, enabled: Boolean, address: String, status: Int) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.enabled = enabled
        this.address = address
        this.status = status
    }
}
