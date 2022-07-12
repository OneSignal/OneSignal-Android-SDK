package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.models.SubscriptionType
import com.onesignal.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor

class CreateSubscriptionOperation(
    val id: String,
    val type: SubscriptionType,
    val address: String) : Operation(SubscriptionOperationExecutor.CREATE_SUBSCRIPTION)  {
}