package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor

class DeleteSubscriptionOperation(val id: String) : Operation(SubscriptionOperationExecutor.DELETE_SUBSCRIPTION)  {
}