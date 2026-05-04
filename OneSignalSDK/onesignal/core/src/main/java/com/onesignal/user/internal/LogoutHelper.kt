package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation

class LogoutHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val lock: Any,
) {
    internal data class LogoutEnqueueContext(
        val appId: String,
        val newOnesignalId: String,
    )

    /**
     * Synchronously switches to a new device-scoped user under the login/logout lock
     * so subsequent SDK calls (e.g. addTag) see the new anonymous user immediately.
     * Returns context needed for [enqueueLogout], or null if no user was logged in
     * (no switch needed).
     */
    internal fun switchUser(): LogoutEnqueueContext? {
        synchronized(lock) {
            if (identityModelStore.model.externalId == null) {
                return null
            }

            // Create new device-scoped user (clears external ID)
            userSwitcher.createAndSwitchToNewUser()

            // TODO: remove JWT Token for all future requests.

            return LogoutEnqueueContext(configModel.appId, identityModelStore.model.onesignalId)
        }
    }

    /**
     * Enqueues the anonymous [LoginUserOperation] for the newly-created device-scoped user.
     */
    internal fun enqueueLogout(context: LogoutEnqueueContext) {
        operationRepo.enqueue(
            LoginUserOperation(
                context.appId,
                context.newOnesignalId,
                null,
                // No external ID for device-scoped user
            ),
        )
    }
}
