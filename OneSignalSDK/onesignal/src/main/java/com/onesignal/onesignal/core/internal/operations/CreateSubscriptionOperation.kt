package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.models.SubscriptionType
import com.onesignal.onesignal.core.internal.operations.impl.executors.SubscriptionOperationExecutor

class CreateSubscriptionOperation(
    val appId: String,
    val id: String,
    val type: SubscriptionType,
    val enabled: Boolean,
    val address: String) : Operation(SubscriptionOperationExecutor.CREATE_SUBSCRIPTION)  {
}