package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.impl.executors.UserOperationExecutor

class CreateUserOperation(val id: String) : Operation(UserOperationExecutor.CREATE_USER)  {
}