package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.UserOperationExecutor

internal class UpdateUserOperation(
    val id: String,
    val property: String,
    val value: Any?
) : Operation(UserOperationExecutor.UPDATE_USER)
