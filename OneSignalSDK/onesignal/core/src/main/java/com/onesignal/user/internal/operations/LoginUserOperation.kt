package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.LoginUserOperationExecutor

/**
 * An [Operation] to login the user with the [externalId] provided.  Logging in a user will do the
 * following:
 *
 * 1. Attempt to give the user identified by [existingOnesignalId] an alias of [externalId]. If
 *    this succeeds the existing user becomes
 */
class LoginUserOperation() : Operation(LoginUserOperationExecutor.LOGIN_USER) {
    /**
     * The application ID the user will exist/be logged in under.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The local OneSignal ID this user was initially logged in under. The user models with this ID
     * will have its ID updated with the backend-generated ID post-create.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * The optional external ID of this newly logged-in user. Must be unique for the [appId].
     */
    var externalId: String?
        get() = getOptStringProperty(::externalId.name)
        set(value) {
            setOptStringProperty(::externalId.name, value)
        }

    /**
     * The user ID of an existing user the [externalId] will be attempted to be associated to first.
     * When null (or non-null but unsuccessful), a new user will be upserted. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var existingOnesignalId: String?
        get() = getOptStringProperty(::existingOnesignalId.name)
        private set(value) {
            setOptStringProperty(::existingOnesignalId.name, value)
        }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String = ""
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.CREATE
    override val canStartExecute: Boolean get() = existingOnesignalId == null || !IDManager.isLocalId(existingOnesignalId!!)
    override val applyToRecordId: String get() = existingOnesignalId ?: onesignalId

    constructor(appId: String, onesignalId: String, externalId: String?, existingOneSignalId: String? = null) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.externalId = externalId
        this.existingOnesignalId = existingOneSignalId
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(existingOnesignalId)) {
            existingOnesignalId = map[existingOnesignalId]!!
        }
    }
}
