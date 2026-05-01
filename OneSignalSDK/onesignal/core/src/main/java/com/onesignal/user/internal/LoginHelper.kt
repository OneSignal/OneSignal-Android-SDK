package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.LoginUserOperation

internal class LoginHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val jwtTokenStore: JwtTokenStore,
    private val lock: Any,
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
                // Even when no user-switch happens, an updated JWT for the same externalId
                // should be stored so subsequent requests use the fresh token.
                if (jwtBearerToken != null) {
                    jwtTokenStore.putJwt(externalId, jwtBearerToken)
                }
                return null
            }

            // Store the JWT before the LoginUserOperation enqueues so that when the op
            // dispatches, the JWT lookup in `hasValidJwtIfRequired` already succeeds.
            if (jwtBearerToken != null) {
                jwtTokenStore.putJwt(externalId, jwtBearerToken)
            }
            userSwitcher.createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            val newOneSignalId = identityModelStore.model.onesignalId
            val existingOneSignalId =
                if (currentExternalId == null) currentOneSignalId else null

            return LoginEnqueueContext(configModel.appId, newOneSignalId, externalId, existingOneSignalId)
        }
    }

    /**
     * Enqueues the [LoginUserOperation] and suspends until it completes.
     */
    internal suspend fun enqueueLogin(context: LoginEnqueueContext) {
        val result =
            operationRepo.enqueueAndWait(
                LoginUserOperation(
                    context.appId,
                    context.newIdentityOneSignalId,
                    context.externalId,
                    context.existingOneSignalId,
                ),
            )

        if (!result) {
            Logging.warn("Could not login user")
        }
    }
}
