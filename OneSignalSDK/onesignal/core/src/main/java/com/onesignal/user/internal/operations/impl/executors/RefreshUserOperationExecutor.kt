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
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
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
    private val _buildUserService: IRebuildUserService,
    private val _newRecordState: NewRecordsState,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(REFRESH_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "RefreshUserOperationExecutor(operation: $operations)")

        if (operations.any { it !is RefreshUserOperation }) {
            throw Exception("Unrecognized operation(s)! Attempted operations:\n$operations")
        }

        val startingOp = operations.first()
        if (startingOp is RefreshUserOperation) {
            return getUser(startingOp)
        }

        throw Exception("Unrecognized operation: $startingOp")
    }

    private suspend fun getUser(op: RefreshUserOperation): ExecutionResponse {
        try {
            val response =
                _userBackend.getUser(
                    op.appId,
                    IdentityConstants.ONESIGNAL_ID,
                    op.onesignalId,
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

            // No longer hydrate timezone from remote, set locally
            propertiesModel.update()

            val subscriptionModels = mutableListOf<SubscriptionModel>()
            for (subscription in response.subscriptions) {
                val subscriptionModel = SubscriptionModel()
                subscriptionModel.id = subscription.id!!
                subscriptionModel.address = subscription.token ?: ""
                subscriptionModel.status = SubscriptionStatus.fromInt(subscription.notificationTypes ?: SubscriptionStatus.SUBSCRIBED.value) ?: SubscriptionStatus.SUBSCRIBED
                subscriptionModel.type =
                    when (subscription.type!!) {
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
                subscriptionModel.optedIn = subscriptionModel.status != SubscriptionStatus.UNSUBSCRIBE && subscriptionModel.status != SubscriptionStatus.DISABLED_FROM_REST_API_DEFAULT_REASON
                subscriptionModel.sdk = subscription.sdk ?: ""
                subscriptionModel.deviceOS = subscription.deviceOS ?: ""
                subscriptionModel.carrier = subscription.carrier ?: ""
                subscriptionModel.appVersion = subscription.appVersion ?: ""

                // We only add a non-push subscriptions. For push, the device is the source of truth
                // so we don't want to cache these subscriptions from the backend.
                if (subscriptionModel.type != SubscriptionType.PUSH) {
                    subscriptionModels.add(subscriptionModel)
                }
            }
            // Retrieve the push subscription ID from the backend configuration model store
            val pushSubscriptionIdFromConfig = _configModelStore.model.pushSubscriptionId

            if (pushSubscriptionIdFromConfig != null) {
                // Retrieve the push subscription model from the model store
                val cachedPushSubscriptionModel = _subscriptionsModelStore.get(pushSubscriptionIdFromConfig)

                // If non-null, the cached push subscription matches the ID coming from backend config
                if (cachedPushSubscriptionModel != null) {
                    subscriptionModels.add(cachedPushSubscriptionModel)
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
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.MISSING -> {
                    if (ex.statusCode == 404 && _newRecordState.isInMissingRetryWindow(op.onesignalId)) {
                        return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    }
                    val operations = _buildUserService.getRebuildOperationsIfCurrentUser(op.appId, op.onesignalId)
                    return if (operations == null) {
                        ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                    } else {
                        ExecutionResponse(
                            ExecutionResult.FAIL_RETRY,
                            operations = operations,
                            retryAfterSeconds = ex.retryAfterSeconds,
                        )
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
