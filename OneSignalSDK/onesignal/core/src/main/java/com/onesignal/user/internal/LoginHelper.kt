package com.onesignal.user.internal

import com.onesignal.LoginData
import com.onesignal.OneSignalUserProfile
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.OperationWaitResult
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.jwt.JwtRequirement
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class LoginHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val jwtTokenStore: JwtTokenStore,
    private val lock: Any,
    private val subscriptionModelStore: SubscriptionModelStore,
) {
    internal data class LoginEnqueueContext(
        val appId: String,
        val newIdentityOneSignalId: String,
        val externalId: String,
        val existingOneSignalId: String?,
    )

    /**
     * Synchronously switches local user models under the login/logout lock so subsequent
     * SDK calls (e.g. addTag) see the new user's identity immediately. Returns context
     * needed for [enqueueLogin], or null if the user was already logged in with [externalId]
     * (no switch needed).
     */
    internal fun switchUser(
        externalId: String,
        jwtBearerToken: String? = null,
    ): LoginEnqueueContext? {
        synchronized(lock) {
            val currentExternalId = identityModelStore.model.externalId
            val currentOneSignalId = identityModelStore.model.onesignalId

            if (currentExternalId == externalId) {
                // Same-user refresh path (e.g. login(sameId, freshJwt) after a 401). Store the
                // fresh token and wake the queue so any ops deferred by `hasValidJwtIfRequired`
                // dispatch immediately — symmetric with `updateUserJwt`. putJwt no-ops on null.
                if (jwtBearerToken != null) {
                    jwtTokenStore.putJwt(externalId, jwtBearerToken)
                    operationRepo.forceExecuteOperations()
                }
                return null
            }

            // Store the JWT before the LoginUserOperation enqueues so that when the op
            // dispatches, the JWT lookup in `hasValidJwtIfRequired` already succeeds.
            // putJwt no-ops on null.
            jwtTokenStore.putJwt(externalId, jwtBearerToken)
            userSwitcher.createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            val newOneSignalId = identityModelStore.model.onesignalId
            // Under IV-required, the merge-anon-into-identified path can't dispatch — the
            // anon user was never created server-side (no JWT) so the local-id reference
            // would deadlock LoginUserOperation.canStartExecute. Skip the link entirely so
            // the executor takes the createUser (upsert) path.
            val existingOneSignalId =
                if (configModel.useIdentityVerification == JwtRequirement.REQUIRED) {
                    null
                } else if (currentExternalId == null) {
                    currentOneSignalId
                } else {
                    null
                }

            return LoginEnqueueContext(configModel.appId, newOneSignalId, externalId, existingOneSignalId)
        }
    }

    /**
     * Enqueues the [LoginUserOperation] and suspends until it completes.
     */
    internal suspend fun enqueueLogin(
        context: LoginEnqueueContext,
        profile: OneSignalUserProfile? = null,
    ): OperationWaitResult {
        val result =
            operationRepo.enqueueAndAwaitResult(
                LoginUserOperation(
                    context.appId,
                    context.newIdentityOneSignalId,
                    context.externalId,
                    context.existingOneSignalId,
                    profile,
                ),
            )

        if (!result.success) {
            Logging.warn("Could not login user: HTTP ${result.httpStatusCode} ${result.httpResponse}")
        }
        return result
    }

    internal fun contextForCurrentUser(externalId: String): LoginEnqueueContext =
        LoginEnqueueContext(
            appId = configModel.appId,
            newIdentityOneSignalId = identityModelStore.model.onesignalId,
            externalId = externalId,
            existingOneSignalId = null,
        )

    internal fun loginDataFromStores(
        externalId: String,
        profile: OneSignalUserProfile,
    ): LoginData {
        val subscriptions = subscriptionModelStore.list()
        return LoginData(
            onesignalId = identityModelStore.model.onesignalId,
            externalId = externalId,
            emailSubscriptionId = subscriptionId(subscriptions, SubscriptionType.EMAIL, profile.email),
            smsSubscriptionId = subscriptionId(subscriptions, SubscriptionType.SMS, profile.phoneNumber),
        )
    }

    private fun subscriptionId(
        subscriptions: Collection<SubscriptionModel>,
        type: SubscriptionType,
        address: String?,
    ): String? {
        if (address.isNullOrBlank()) return null
        return subscriptions.firstOrNull { it.type == type && it.address == address }?.id
    }
}
