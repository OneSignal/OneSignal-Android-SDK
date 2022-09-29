package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.IdentityOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to delete an alias from the OneSignal backend.
 */
internal class DeleteAliasOperation(
    /**
     * The application ID this alias that is to be deleted.
     */
    val appId: String,

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    val onesignalId: String,

    /**
     * The alias label to be deleted.
     */
    val label: String
) : Operation(IdentityOperationExecutor.DELETE_ALIAS) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Alias.$label"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::label.name, label)
    }
}
