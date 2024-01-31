package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class RefreshUserOperationExecutor(
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _subscriptionsModelStore: SubscriptionModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _buildUserService: IRebuildUserService
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(REFRESH_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "RefreshUserOperationExecutor(operation: $operations)")

        val startingOp = operations.first()
        if (startingOp is RefreshUserOperation) {
            return getUser(startingOp)
        }

        throw Exception("Unrecognized operation: $startingOp")
    }

    private suspend fun getUser(op: RefreshUserOperation): ExecutionResponse {
        try {
            val response = _userBackend.getUser(
                op.appId,
                IdentityConstants.ONESIGNAL_ID,
                op.onesignalId
            )

            if (op.onesignalId != _identityModelStore.model.onesignalId) {
                return ExecutionResponse(ExecutionResult.SUCCESS)
            }

            val identityModel = IdentityModel()
            for (aliasKVP in response.identities) {
                identityModel[aliasKVP.key] = aliasKVP.value
            }

            val propertiesModel = PropertiesModel()
            propertiesModel.onesignalId = op.onesignalId

            if (response.properties.country != null) {
                propertiesModel.country = response.properties.country
            }

            if (response.properties.language != null) {
                propertiesModel.language = response.properties.language
            }

            if (response.properties.tags != null) {
                for (tagKVP in response.properties.tags) {
                    if (tagKVP.value != null) {
                        propertiesModel.tags[tagKVP.key] = tagKVP.value!!
                    }
                }
            }

            if (response.properties.timezoneId != null) {
                propertiesModel.timezone = response.properties.timezoneId
            }

            val subscriptionModels = mutableListOf<SubscriptionModel>()
            for (subscription in response.subscriptions) {
                val subscriptionModel = SubscriptionModel()
                subscriptionModel.id = subscription.id
                subscriptionModel.address = subscription.token ?: ""
                subscriptionModel.status = SubscriptionStatus.fromInt(subscription.notificationTypes ?: SubscriptionStatus.SUBSCRIBED.value) ?: SubscriptionStatus.SUBSCRIBED
                subscriptionModel.type = when (subscription.type) {
                    SubscriptionObjectType.EMAIL -> {
                        SubscriptionType.EMAIL
                    }
                    SubscriptionObjectType.SMS -> {
                        SubscriptionType.SMS
                    }
                    else -> {
                        SubscriptionType.PUSH
                    }
                }
                subscriptionModel.optedIn = subscriptionModel.status != SubscriptionStatus.UNSUBSCRIBE

                // We only add a push subscription if it is this device's push subscription.
                if (subscriptionModel.type != SubscriptionType.PUSH || subscriptionModel.id == _configModelStore.model.pushSubscriptionId) {
                    subscriptionModels.add(subscriptionModel)
                }
            }

            _identityModelStore.replace(identityModel, ModelChangeTags.HYDRATE)
            _propertiesModelStore.replace(propertiesModel, ModelChangeTags.HYDRATE)
            _subscriptionsModelStore.replaceAll(subscriptionModels, ModelChangeTags.HYDRATE)

            return ExecutionResponse(ExecutionResult.SUCCESS)
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED)
                NetworkUtils.ResponseStatusType.MISSING -> {
                    val operations = _buildUserService.getRebuildOperationsIfCurrentUser(op.appId, op.onesignalId)
                    if (operations == null) {
                        return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                    } else {
                        return ExecutionResponse(ExecutionResult.FAIL_RETRY, operations = operations)
                    }
                }
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }
    }

    companion object {
        const val REFRESH_USER = "refresh-user"
    }
}
