package com.onesignal.user.internal

import com.onesignal.LoginData
import com.onesignal.OneSignalUserProfile
import com.onesignal.common.IDManager
import com.onesignal.common.PIIHasher
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.LoginWaitMetadata
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

    internal data class LoginSwitchResult(
        val context: LoginEnqueueContext?,
        val onesignalId: String,
    )

    /**
     * Switch local identity under the login lock. Null [LoginSwitchResult.context] means do not enqueue.
     */
    internal fun switchUser(
        externalId: String,
        jwtBearerToken: String? = null,
        profile: OneSignalUserProfile? = null,
    ): LoginSwitchResult {
        synchronized(lock) {
            val currentExternalId = identityModelStore.model.externalId
            val currentOneSignalId = identityModelStore.model.onesignalId

            if (currentExternalId == externalId) {
                if (jwtBearerToken != null) {
                    jwtTokenStore.putJwt(externalId, jwtBearerToken)
                    operationRepo.forceExecuteOperations()
                }
                val retryOrUpsert = IDManager.isLocalId(currentOneSignalId) || profile?.hasFields == true
                val context =
                    if (retryOrUpsert) {
                        LoginEnqueueContext(configModel.appId, currentOneSignalId, externalId, null)
                    } else {
                        null
                    }
                return LoginSwitchResult(context, currentOneSignalId)
            }

            jwtTokenStore.putJwt(externalId, jwtBearerToken)
            userSwitcher.createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            val newOneSignalId = identityModelStore.model.onesignalId
            val existingOneSignalId =
                if (configModel.useIdentityVerification == JwtRequirement.REQUIRED) {
                    null
                } else if (currentExternalId == null) {
                    currentOneSignalId
                } else {
                    null
                }

            return LoginSwitchResult(
                LoginEnqueueContext(configModel.appId, newOneSignalId, externalId, existingOneSignalId),
                newOneSignalId,
            )
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
            Logging.warn("Could not login user: HTTP ${result.httpStatusCode} ${result.httpResponse}. Local identity is already ${context.externalId}.")
        }
        return result
    }

    internal fun loginData(
        externalId: String,
        profile: OneSignalUserProfile,
        wait: OperationWaitResult? = null,
        fallbackOnesignalId: String? = null,
    ): LoginData {
        val subscriptions = subscriptionModelStore.list()
        val meta = wait?.metadata as? LoginWaitMetadata
        return LoginData(
            onesignalId = meta?.onesignalId ?: fallbackOnesignalId ?: identityModelStore.model.onesignalId,
            externalId = externalId,
            emailSubscriptionId = meta?.emailSubscriptionId
                ?: subscriptionId(subscriptions, SubscriptionType.EMAIL, profile.email),
            smsSubscriptionId = meta?.smsSubscriptionId
                ?: subscriptionId(subscriptions, SubscriptionType.SMS, profile.phoneNumber),
        )
    }

    internal fun loginDataFromStores(
        externalId: String,
        profile: OneSignalUserProfile,
    ): LoginData = loginData(externalId, profile)

    private fun subscriptionId(
        subscriptions: Collection<SubscriptionModel>,
        type: SubscriptionType,
        address: String?,
    ): String? {
        if (address.isNullOrBlank()) return null
        val hashed = PIIHasher.hash(address)
        val ignoreCase = type == SubscriptionType.EMAIL
        return subscriptions.firstOrNull { sub ->
            sub.type == type && (sub.address.equals(address, ignoreCase) || sub.address == hashed)
        }?.id
    }
}
