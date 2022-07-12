package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.internal.backend.api.IApiService
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.*

class SubscriptionOperationExecutor(
    private val _api: IApiService) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(CREATE_SUBSCRIPTION, UPDATE_SUBSCRIPTION, DELETE_SUBSCRIPTION)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "SubscriptionOperationExecutor(operation: $operation)")

        if(operation is CreateSubscriptionOperation) {
            _api.createSubscriptionAsync("","", null)
        }
        else if(operation is DeleteSubscriptionOperation) {
            _api.deleteSubscriptionAsync(operation.id)
        }
        else if(operation is UpdateSubscriptionOperation) {
            _api.updateSubscriptionAsync()
        }
    }

    companion object {
        const val CREATE_SUBSCRIPTION = "create-subscription"
        const val UPDATE_SUBSCRIPTION = "update-subscription"
        const val DELETE_SUBSCRIPTION = "delete-subscription"
    }
}