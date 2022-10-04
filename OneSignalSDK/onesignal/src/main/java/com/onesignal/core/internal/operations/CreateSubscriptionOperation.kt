package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.impl.SubscriptionOperationExecutor

/**
 * An [Operation] to create a new subscription in the OneSignal backend. The subscription wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
internal class CreateSubscriptionOperation() : Operation(SubscriptionOperationExecutor.CREATE_SUBSCRIPTION) {
    /**
     * The application ID this subscription will be created under.
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
     * The local ID of the subscription being created.  The subscription model with this ID will have its
     * ID updated with the backend-generated ID post-create.
     */
    var subscriptionId: String
        get() = getProperty(::subscriptionId.name)
        private set(value) { setProperty(::subscriptionId.name, value) }

    /**
     * The type of subscription.
     */
    var type: SubscriptionType
        get() = SubscriptionType.valueOf(getProperty(::type.name))
        private set(value) { setProperty(::type.name, value.toString()) }

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

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Subscription.$subscriptionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, subscriptionId: String, type: SubscriptionType, enabled: Boolean, address: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.type = type
        this.enabled = enabled
        this.address = address
    }
}
