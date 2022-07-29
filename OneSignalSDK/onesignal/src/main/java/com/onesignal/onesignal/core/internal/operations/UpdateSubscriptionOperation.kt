package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.impl.executors.SubscriptionOperationExecutor

class UpdateSubscriptionOperation(
    val id: String,
    val property: String,
    val value: Any?) : Operation(SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION)  {
}