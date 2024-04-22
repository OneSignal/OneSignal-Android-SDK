package com.onesignal.user.internal.operations

import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.operations.impl.executors.LoginUserFromSubscriptionOperationExecutor

/**
 * An [Operation] to login the user with the [subscriptionId] provided.
 */
class LoginUserFromSubscriptionOperation() : Operation(LoginUserFromSubscriptionOperationExecutor.LOGIN_USER_FROM_SUBSCRIPTION_USER) {
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
    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        private set(value) {
            setStringProperty(::subscriptionId.name, value)
        }

    override val createComparisonKey: String get() = "$appId.Subscription.$subscriptionId.Login"
    override val modifyComparisonKey: String get() = "$appId.Subscription.$subscriptionId.Login"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean = true
    override val applyToRecordId: String get() = subscriptionId

    constructor(appId: String, onesignalId: String, subscriptionId: String) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
    }
}
