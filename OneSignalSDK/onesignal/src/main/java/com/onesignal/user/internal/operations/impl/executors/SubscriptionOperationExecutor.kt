package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class SubscriptionOperationExecutor(
    private val _subscriptionBackend: ISubscriptionBackendService,
    private val _deviceService: IDeviceService,
    private val _subscriptionModelStore: SubscriptionModelStore
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(CREATE_SUBSCRIPTION, UPDATE_SUBSCRIPTION, DELETE_SUBSCRIPTION)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "SubscriptionOperationExecutor(operations: $operations)")

        val startingOp = operations.first()

        return if (startingOp is CreateSubscriptionOperation) {
            createSubscription(startingOp, operations)
        } else if (operations.any { it is DeleteSubscriptionOperation }) {
            deleteSubscription(operations.first { it is DeleteSubscriptionOperation } as DeleteSubscriptionOperation)
        } else if (startingOp is UpdateSubscriptionOperation) {
            updateSubscription(startingOp, operations)
        } else {
            throw Exception("Unrecognized operation: $startingOp")
        }
    }

    private suspend fun createSubscription(createOperation: CreateSubscriptionOperation, operations: List<Operation>): ExecutionResponse {
        // if there are any deletes all operations should be tossed, nothing to do.
        if (operations.any { it is DeleteSubscriptionOperation }) {
            return ExecutionResponse(ExecutionResult.SUCCESS)
        }

        // the effective enabled/address for this subscription is the last update performed, if there is one. If there
        // isn't one we fall back to whatever is in the create operation.
        val lastUpdateOperation = operations.lastOrNull { it is UpdateSubscriptionOperation } as UpdateSubscriptionOperation?
        val enabled = lastUpdateOperation?.enabled ?: createOperation.enabled
        val address = lastUpdateOperation?.address ?: createOperation.address
        val status = lastUpdateOperation?.status ?: createOperation.status

        // translate the subscription type to the subscription object type.
        val subscriptionType: SubscriptionObjectType = when (createOperation.type) {
            SubscriptionType.SMS -> {
                SubscriptionObjectType.SMS
            }
            SubscriptionType.EMAIL -> {
                SubscriptionObjectType.SMS
            }
            else -> {
                SubscriptionObjectType.fromDeviceType(_deviceService.deviceType)
            }
        }

        try {
            val backendSubscriptionId = _subscriptionBackend.createSubscription(
                createOperation.appId,
                IdentityConstants.ONESIGNAL_ID,
                createOperation.onesignalId,
                subscriptionType,
                enabled,
                address,
                status
            )

            val subscriptionModel = _subscriptionModelStore.get(createOperation.subscriptionId)
            if (subscriptionModel != null) {
                subscriptionModel.setProperty(
                    SubscriptionModel::id.name,
                    backendSubscriptionId,
                    ModelChangeTags.HYDRATE
                )
            } else {
                // the subscription model no longer exists, add it back to the model as a HYDRATE
                val newSubModel = SubscriptionModel()
                newSubModel.id = backendSubscriptionId
                newSubModel.type = createOperation.type
                newSubModel.address = address
                newSubModel.enabled = enabled
                newSubModel.status = status

                _subscriptionModelStore.add(newSubModel, ModelChangeTags.HYDRATE)
            }

            return ExecutionResponse(ExecutionResult.SUCCESS, mapOf(createOperation.subscriptionId to backendSubscriptionId))
        } catch (ex: BackendException) {
            return if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                ExecutionResponse(ExecutionResult.FAIL_RETRY)
            } else {
                ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }
    }

    private suspend fun updateSubscription(startingOperation: UpdateSubscriptionOperation, operations: List<Operation>): ExecutionResponse {
        // the effective enabled/address is the last update performed
        val lastOperation = operations.last() as UpdateSubscriptionOperation
        try {
            _subscriptionBackend.updateSubscription(
                lastOperation.appId,
                lastOperation.subscriptionId,
                lastOperation.enabled,
                lastOperation.address,
                lastOperation.status
            )

            // update/create the subscription model, in case it lost it's sync.
            val subscriptionModel = _subscriptionModelStore.get(lastOperation.subscriptionId)
            if (subscriptionModel != null) {
                subscriptionModel.setProperty(SubscriptionModel::type.name, lastOperation.type.toString(), ModelChangeTags.HYDRATE)
                subscriptionModel.setProperty(SubscriptionModel::address.name, lastOperation.address, ModelChangeTags.HYDRATE)
                subscriptionModel.setProperty(SubscriptionModel::enabled.name, lastOperation.enabled, ModelChangeTags.HYDRATE)
                subscriptionModel.setProperty(SubscriptionModel::status.name, lastOperation.status, ModelChangeTags.HYDRATE)
            } else {
                val newSubModel = SubscriptionModel()
                newSubModel.id = lastOperation.subscriptionId
                newSubModel.type = lastOperation.type
                newSubModel.address = lastOperation.address
                newSubModel.enabled = lastOperation.enabled
                newSubModel.status = lastOperation.status

                _subscriptionModelStore.add(newSubModel, ModelChangeTags.HYDRATE)
            }
        } catch (ex: BackendException) {
            return if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                ExecutionResponse(ExecutionResult.FAIL_RETRY)
            } else {
                ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    private suspend fun deleteSubscription(op: DeleteSubscriptionOperation): ExecutionResponse {
        try {
            _subscriptionBackend.deleteSubscription(op.appId, op.subscriptionId)

            // remove the subscription model as a HYDRATE in case for some reason it still exists.
            _subscriptionModelStore.remove(op.subscriptionId, ModelChangeTags.HYDRATE)
        } catch (ex: BackendException) {
            return if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                ExecutionResponse(ExecutionResult.FAIL_RETRY)
            } else {
                ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    companion object {
        const val CREATE_SUBSCRIPTION = "create-subscription"
        const val UPDATE_SUBSCRIPTION = "update-subscription"
        const val DELETE_SUBSCRIPTION = "delete-subscription"
    }
}
