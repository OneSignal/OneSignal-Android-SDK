package com.onesignal.user.internal.operations

import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.UserOperationExecutor

/**
 * An [Operation] to create a new user in the OneSignal backend. The user wll
 * be associated to the [appId] provided.
 */
class CreateUserOperation() : Operation(UserOperationExecutor.CREATE_USER) {
    /**
     * The application ID this user will be created under.
     */
    var appId: String
        get() = getProperty(::appId.name)
        private set(value) { setProperty(::appId.name, value) }

    /**
     * The local OneSignal ID this user was initially created under. The user models with this ID
     * will have its ID updated with the backend-generated ID post-create.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        private set(value) { setProperty(::onesignalId.name, value) }

    /**
     * The optional external ID of this newly created user. Must be unique for the [appId].
     */
    var externalId: String?
        get() = getProperty(::externalId.name)
        private set(value) { setProperty(::externalId.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String = ""
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.CREATE
    override val canStartExecute: Boolean = true

    constructor(appId: String, onesignalId: String, externalId: String?) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.externalId = externalId
    }
}
