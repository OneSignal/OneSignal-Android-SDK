package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.SubscriptionOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to delete a subscription from the OneSignal backend.
 */
internal class DeleteSubscriptionOperation(
    /**
     * The application ID this subscription that is to be deleted.
     */
    val appId: String,

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val onesignalId: String,

    /**
     * The subscription ID that is to be deleted. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val subscriptionId: String
) : Operation(SubscriptionOperationExecutor.DELETE_SUBSCRIPTION) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Subscription.$subscriptionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId) && !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::subscriptionId.name, subscriptionId)
    }
}
