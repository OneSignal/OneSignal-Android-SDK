package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.UserOperationExecutor

internal class CreateUserOperation(val id: String) : Operation(UserOperationExecutor.CREATE_USER)
