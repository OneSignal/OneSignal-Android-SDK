package com.onesignal.core.internal.operations

import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.operations.impl.IdentityOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to create a new alias in the OneSignal backend. The alias wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
internal class SetAliasOperation(
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
     * The alias label.
     */
    val label: String,

    /**
     * The alias value.
     */
    val value: String
) : Operation(IdentityOperationExecutor.SET_ALIAS) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Identity.$label"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::onesignalId.name, onesignalId)
            .put(::label.name, label)
            .put(::value.name, value)
    }
}
