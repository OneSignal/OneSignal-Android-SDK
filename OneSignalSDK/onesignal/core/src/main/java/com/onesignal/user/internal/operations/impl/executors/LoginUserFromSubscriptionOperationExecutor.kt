package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore

internal class LoginUserFromSubscriptionOperationExecutor(
    private val _subscriptionBackend: ISubscriptionBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _configModelStore: ConfigModelStore,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(LOGIN_USER_FROM_SUBSCRIPTION_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.debug("LoginUserFromSubscriptionOperationExecutor(operation: $operations)")

        if (operations.size > 1) {
            throw Exception("Only supports one operation! Attempted operations:\n$operations")
        }

        val startingOp = operations.first()

        if (startingOp is LoginUserFromSubscriptionOperation) {
            return loginUser(startingOp)
        }

        throw Exception("Unrecognized operation: $startingOp")
    }

    private suspend fun loginUser(loginUserOp: LoginUserFromSubscriptionOperation): ExecutionResponse {
        try {
            // Not allowed when identity verification is on
            if (_configModelStore.model.useIdentityVerification) {
                return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }

            val identities =
                _subscriptionBackend.getIdentityFromSubscription(
                    loginUserOp.appId,
                    loginUserOp.subscriptionId,
                )
            val backendOneSignalId = identities.getOrDefault(IdentityConstants.ONESIGNAL_ID, null)

            if (backendOneSignalId == null) {
                Logging.warn("Subscription ${loginUserOp.subscriptionId} has no ${IdentityConstants.ONESIGNAL_ID}!")
                return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }

            val idTranslations = mutableMapOf<String, String>()
            // Add the "local-to-backend" ID translation to the IdentifierTranslator for any operations that were
            // *not* executed but still reference the locally-generated IDs.
            // Update the current identity, property, and subscription models from a local ID to the backend ID
            idTranslations[loginUserOp.onesignalId] = backendOneSignalId

            val identityModel = _identityModelStore.model
            val propertiesModel = _propertiesModelStore.model

            if (identityModel.onesignalId == loginUserOp.onesignalId) {
                identityModel.setStringProperty(IdentityConstants.ONESIGNAL_ID, backendOneSignalId, ModelChangeTags.HYDRATE)
            }

            if (propertiesModel.onesignalId == loginUserOp.onesignalId) {
                propertiesModel.setStringProperty(PropertiesModel::onesignalId.name, backendOneSignalId, ModelChangeTags.HYDRATE)
            }

            return ExecutionResponse(
                ExecutionResult.SUCCESS,
                idTranslations,
                listOf(RefreshUserOperation(loginUserOp.appId, backendOneSignalId)),
            )
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED -> {
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED)
                }
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }
    }

    companion object {
        const val LOGIN_USER_FROM_SUBSCRIPTION_USER = "login-user-from-subscription"
    }
}
