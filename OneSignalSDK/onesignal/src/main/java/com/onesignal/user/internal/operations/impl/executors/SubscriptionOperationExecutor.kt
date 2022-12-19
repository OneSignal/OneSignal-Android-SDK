package com.onesignal.user.internal.operations.impl.executors

import android.os.Build
import com.onesignal.common.DeviceUtils
import com.onesignal.common.NetworkUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.RootToolsInternalMethods
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObject
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
    private val _applicationService: IApplicationService,
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

        try {
            val subscription = SubscriptionObject(
                createOperation.subscriptionId,
                convert(createOperation.type),
                address,
                enabled,
                status.value,
                OneSignalUtils.sdkVersion,
                Build.MODEL,
                Build.VERSION.RELEASE,
                RootToolsInternalMethods.isRooted,
                DeviceUtils.getNetType(_applicationService.appContext),
                DeviceUtils.getCarrierName(_applicationService.appContext)
            )

            val backendSubscriptionId = _subscriptionBackend.createSubscription(
                createOperation.appId,
                IdentityConstants.ONESIGNAL_ID,
                createOperation.onesignalId,
                subscription
            )

            // update the subscription model with the new ID, if it's still active.
            val subscriptionModel = _subscriptionModelStore.get(createOperation.subscriptionId)
            subscriptionModel?.setStringProperty(SubscriptionModel::id.name, backendSubscriptionId, ModelChangeTags.HYDRATE)

            return ExecutionResponse(ExecutionResult.SUCCESS, mapOf(createOperation.subscriptionId to backendSubscriptionId))
        } catch (ex: BackendException) {
            return if (ex.statusCode == 409) {
                ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            } else if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
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
            val subscription = SubscriptionObject(
                lastOperation.subscriptionId,
                convert(lastOperation.type),
                lastOperation.address,
                lastOperation.enabled,
                lastOperation.status.value,
                OneSignalUtils.sdkVersion,
                Build.MODEL,
                Build.VERSION.RELEASE,
                RootToolsInternalMethods.isRooted,
                DeviceUtils.getNetType(_applicationService.appContext),
                DeviceUtils.getCarrierName(_applicationService.appContext)
            )

            _subscriptionBackend.updateSubscription(lastOperation.appId, lastOperation.subscriptionId, subscription)
        } catch (ex: BackendException) {
            return if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                ExecutionResponse(ExecutionResult.FAIL_RETRY)
            } else {
                ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    private fun convert(subscriptionType: SubscriptionType): SubscriptionObjectType {
        return when (subscriptionType) {
            SubscriptionType.SMS -> {
                SubscriptionObjectType.SMS
            }
            SubscriptionType.EMAIL -> {
                SubscriptionObjectType.EMAIL
            }
            else -> {
                SubscriptionObjectType.fromDeviceType(_deviceService.deviceType)
            }
        }
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
