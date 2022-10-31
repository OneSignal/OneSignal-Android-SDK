package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class RefreshUserOperationExecutor(
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _subscriptionsModelStore: SubscriptionModelStore
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(REFRESH_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "GetUserOperationExecutor(operation: $operations)")

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

            val identityModel = IdentityModel()
            identityModel.onesignalId = op.onesignalId
            // TODO: Remove once we can pull this from the backend.
            identityModel.externalId = _identityModelStore.model.externalId
            for (aliasKVP in response.identities) {
                identityModel[aliasKVP.key] = aliasKVP.value
            }

            val propertiesModel = PropertiesModel()
            propertiesModel.onesignalId = op.onesignalId

            if (response.properties.country != null) {
                propertiesModel.country = response.properties.country!!
            }

            propertiesModel.setProperty(PropertiesModel::language.name, response.properties.language, ModelChangeTags.HYDRATE)

            if (response.properties.tags != null) {
                for (tagKVP in response.properties.tags!!) {
                    propertiesModel.tags[tagKVP.key] = tagKVP.value
                }
            }

            if (response.properties.timezoneId != null) {
                propertiesModel.timezone = response.properties.timezoneId!!
            }

            val subscriptionModels = mutableListOf<SubscriptionModel>()
            // TODO fill this in for real. Below just copies over the push subscription.
            val existingPush = _subscriptionsModelStore.list().firstOrNull { it.type == SubscriptionType.PUSH }
            if (existingPush != null) {
                subscriptionModels.add(existingPush)
            }

            _identityModelStore.replace(identityModel, ModelChangeTags.HYDRATE)
            _propertiesModelStore.replace(propertiesModel, ModelChangeTags.HYDRATE)
            _subscriptionsModelStore.replaceAll(subscriptionModels, ModelChangeTags.HYDRATE)

            return ExecutionResponse(ExecutionResult.SUCCESS)
        } catch (ex: BackendException) {
            return if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                ExecutionResponse(ExecutionResult.FAIL_RETRY)
            } else {
                ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }
    }

    companion object {
        const val REFRESH_USER = "refresh-user"
    }
}
