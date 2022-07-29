package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.impl.executors.UserOperationExecutor

class UpdateUserOperation(
    val id: String,
    val property: String,
    val value: Any?) : Operation(UserOperationExecutor.UPDATE_USER)  {
}