package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to create/update a tag in the OneSignal backend. The tag wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
internal class SetTagOperation(
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
     * The tag key.
     */
    val key: String,

    /**
     * The new/updated tag value.
     */
    val value: String
) : Operation(UserOperationExecutor.SET_TAG) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = createComparisonKey
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::key.name, key)
            .put(::value.name, value)
    }
}
