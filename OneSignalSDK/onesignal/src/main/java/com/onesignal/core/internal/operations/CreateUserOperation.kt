package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.UserOperationExecutor

internal class CreateUserOperation(
    val appId: String,
    val sdkId: String,
    val pushToken: String?
) : Operation(UserOperationExecutor.CREATE_USER)
