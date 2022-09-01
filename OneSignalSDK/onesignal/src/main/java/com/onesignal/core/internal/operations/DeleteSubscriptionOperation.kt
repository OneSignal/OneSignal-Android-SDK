package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor

class DeleteSubscriptionOperation(val id: String) : Operation(SubscriptionOperationExecutor.DELETE_SUBSCRIPTION)
