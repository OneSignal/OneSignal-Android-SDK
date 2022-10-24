package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.IDManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.device.IDeviceService
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

    override suspend fun execute(operations: List<Operation>) {
        Logging.log(LogLevel.DEBUG, "SubscriptionOperationExecutor(operations: $operations)")

        val startingOp = operations.first()

        if (startingOp is CreateSubscriptionOperation) {
            createSubscription(startingOp, operations)
        } else if (operations.any { it is DeleteSubscriptionOperation }) {
            deleteSubscription(operations.first { it is DeleteSubscriptionOperation } as DeleteSubscriptionOperation)
        } else if (startingOp is UpdateSubscriptionOperation) {
            updateSubscription(startingOp, operations)
        }
    }

    private suspend fun createSubscription(createOperation: CreateSubscriptionOperation, operations: List<Operation>) {
        // if there are any deletes all operations should be tossed, nothing to do.
        if (operations.any { it is DeleteSubscriptionOperation }) {
            return
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
                IDManager.retrieveId(createOperation.onesignalId),
                subscriptionType,
                enabled,
                address,
                status
            )

            // Add the "local-to-backend" ID translation to the IdentifierTranslator for any operations that were
            // *not* executed but still reference the locally-generated IDs.
            // Update the current identity, property, and subscription models from a local ID to the backend ID
            IDManager.setLocalToBackendIdTranslation(createOperation.subscriptionId, backendSubscriptionId)

            val subscriptionModel = _subscriptionModelStore.get(createOperation.subscriptionId)
            subscriptionModel?.setProperty(SubscriptionModel::id.name, backendSubscriptionId, ModelChangeTags.HYDRATE)
        } catch (ex: BackendException) {
            // TODO: Error handling
        }
    }

    private suspend fun updateSubscription(startingOperation: UpdateSubscriptionOperation, operations: List<Operation>) {
        // the effective enabled/address is the last update performed
        val lastOperation = operations.last() as UpdateSubscriptionOperation
        try {
            _subscriptionBackend.updateSubscription(
                lastOperation.appId,
                IDManager.retrieveId(lastOperation.subscriptionId),
                lastOperation.enabled,
                lastOperation.address,
                lastOperation.status
            )
        } catch (ex: BackendException) {
            // TODO: Need a concept of retrying on network error, and other error handling
        }
    }

    private suspend fun deleteSubscription(op: DeleteSubscriptionOperation) {
        try {
            _subscriptionBackend.deleteSubscription(op.appId, op.subscriptionId)
        } catch (ex: BackendException) {
            // TODO: Need a concept of retrying on network error, and other error handling
        }
    }

    companion object {
        const val CREATE_SUBSCRIPTION = "create-subscription"
        const val UPDATE_SUBSCRIPTION = "update-subscription"
        const val DELETE_SUBSCRIPTION = "delete-subscription"
    }
}
