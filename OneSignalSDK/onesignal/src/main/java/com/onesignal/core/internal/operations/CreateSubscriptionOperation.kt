package com.onesignal.core.internal.operations

import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor

class CreateSubscriptionOperation(
    val appId: String,
    val id: String,
    val type: SubscriptionType,
    val enabled: Boolean,
    val address: String
) : Operation(SubscriptionOperationExecutor.CREATE_SUBSCRIPTION)
