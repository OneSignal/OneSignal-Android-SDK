package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.impl.executors.UserOperationExecutor

class DeleteUserOperation(val id: String) : Operation(UserOperationExecutor.DELETE_USER)  {
}