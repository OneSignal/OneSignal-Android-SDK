package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor

/**
 * An [Operation] to create a new alias in the OneSignal backend. The alias wll
 * be associated to the user with the [appId] and [onesignalId] provided.
 */
class SetAliasOperation() : Operation(IdentityOperationExecutor.SET_ALIAS) {
    /**
     * The application ID this subscription will be created under.
     */
    var appId: String
        get() = getProperty(::appId.name)
        private set(value) { setProperty(::appId.name, value) }

    /**
     * The user ID this subscription will be associated with. This ID *may* be locally generated
     * and should go through [IDManager] to ensure correct processing.
     */
    var onesignalId: String
        get() = getProperty(::onesignalId.name)
        private set(value) { setProperty(::onesignalId.name, value) }

    /**
     * The alias label.
     */
    var label: String
        get() = getProperty(::label.name)
        private set(value) { setProperty(::label.name, value) }

    /**
     * The alias value.
     */
    var value: String
        get() = getProperty(::value.name)
        private set(value) { setProperty(::value.name, value) }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.Identity.$label"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.ALTER
    override val canStartExecute: Boolean get() = !IDManager.isIdLocalOnly(onesignalId)

    constructor(appId: String, onesignalId: String, label: String, value: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.label = label
        this.value = value
    }
}
