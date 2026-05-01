package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore

internal class LogoutHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val subscriptionModelStore: SubscriptionModelStore,
    private val identityVerificationService: IdentityVerificationService,
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

            // Outer gate: dispatch to IV extension only on new code paths. The extension's
            // inner gate (ivBehaviorActive) keeps Phase 3 users on the legacy logout flow.
            val handled =
                identityVerificationService.newCodePathsRun &&
                    switchUserIv(
                        userSwitcher,
                        subscriptionModelStore,
                        configModel,
                        identityVerificationService.ivBehaviorActive,
                    )
            if (handled) {
                // IV-required: subscription is internally disabled and the user-switch
                // suppressed backend op enqueue. Don't enqueue anonymous LoginUserOperation —
                // the anonymous user cannot authenticate without a JWT.
                return null
            }

            // Create new device-scoped user (clears external ID)
            userSwitcher.createAndSwitchToNewUser()

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
