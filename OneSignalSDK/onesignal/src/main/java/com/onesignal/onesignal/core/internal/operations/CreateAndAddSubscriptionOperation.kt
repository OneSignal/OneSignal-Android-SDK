package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.models.SubscriptionType
import com.onesignal.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor

class CreateAndAddSubscriptionOperation(
    val appId: String,
    val type: SubscriptionType,
    val enabled: Boolean,
    val address: String
) : Operation(SubscriptionOperationExecutor.CREATE_AND_ADD_SUBSCRIPTION)
