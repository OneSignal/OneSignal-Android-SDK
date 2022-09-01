package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.UserOperationExecutor

internal class DeleteUserOperation(val id: String) : Operation(UserOperationExecutor.DELETE_USER)
