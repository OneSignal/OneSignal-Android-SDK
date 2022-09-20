package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to delete a tag from the OneSignal backend. The tag wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
internal class DeleteTagOperation(
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
     * The tag key to delete.
     */
    val key: String
) : Operation(UserOperationExecutor.DELETE_TAG) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = createComparisonKey
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::key.name, key)
    }
}
