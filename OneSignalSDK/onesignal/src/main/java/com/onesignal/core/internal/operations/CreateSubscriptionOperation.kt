package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.impl.SubscriptionOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to create a new subscription in the OneSignal backend. The subscription wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
internal class CreateSubscriptionOperation(
    /**
     * The application ID this subscription will be created under.
     */
    val appId: String,

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val onesignalId: String,

    /**
     * The local ID of the subscription being created.  The subscription model with this ID will have its
     * ID updated with the backend-generated ID post-create.
     */
    val subscriptionId: String,

    /**
     * The type of subscription.
     */
    val type: SubscriptionType,

    /**
     * Whether this subscription is currently enabled.
     */
    val enabled: Boolean,

    /**
     * The address-specific information for this subscription. Its contents depends on the type
     * of subscription:
     *
     * * [SubscriptionType.EMAIL]: An email address.
     * * [SubscriptionType.SMS]: A phone number in E.164 format.
     * * [SubscriptionType.PUSH]: The push token.
     */
    val address: String
) : Operation(SubscriptionOperationExecutor.CREATE_SUBSCRIPTION) {
    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Subscription.$subscriptionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::type.name, type)
            .put(::enabled.name, enabled)
            .put(::address.name, address)
            .put(::subscriptionId.name, subscriptionId)
    }
}
