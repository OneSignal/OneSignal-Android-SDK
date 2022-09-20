package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.impl.UserOperationExecutor
import org.json.JSONObject

/**
 * An [Operation] to create a new user in the OneSignal backend. The user wll
 * be associated to the [appId] provided.
 */
internal class CreateUserOperation(
    /**
     * The application ID this user will be created under.
     */
    val appId: String,

    /**
     * The local OneSignal ID this user was initially created under. The user models with this ID
     * will have its ID updated with the backend-generated ID post-create.
     */
    val onesignalId: String,

    /**
     * The optional external ID of this newly created user. Must be unique for the [appId].
     */
    val externalId: String?
) : Operation(UserOperationExecutor.CREATE_USER) {

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String = ""
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.CREATE
    override val canStartExecute: Boolean = true

    override fun toJSON(): JSONObject {
        return JSONObject()
            .put(::appId.name, appId)
            .put(::externalId.name, externalId)
            .put(::onesignalId.name, onesignalId)
    }
}
