package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to update a property related to a specific user.
 */
internal class SetPropertyOperation(
    /**
     * The OneSignal appId the purchase was captured under.
     */
    val appId: String,

    /**
     * The OneSignal ID the purchase was captured under. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val onesignalId: String,

    /**
     * The property that is to be updated against the user.
     */
    val property: String,

    /**
     * The value of that property to update it to.
     */
    val value: Any?
) : Operation(UserOperationExecutor.SET_PROPERTY) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = createComparisonKey
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::property.name, property)
            .put(::value.name, value)
    }
}
