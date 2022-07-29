package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.internal.backend.api.IApiService
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.*

class UserOperationExecutor(
    private val _api: IApiService) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(CREATE_USER, UPDATE_USER, DELETE_USER)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "UserOperationExecutor(operation: $operation)")

        if(operation is CreateUserOperation) {
            _api.createUserAsync(null)
        }
        else if(operation is DeleteUserOperation) {
            _api.deleteUserAsync("external_id", operation.id)
        }
        else if(operation is UpdateUserOperation) {
            _api.updateUserAsync("external_id", operation.id, null)
        }
    }

    companion object {
        const val CREATE_USER = "create-user"
        const val UPDATE_USER = "update-user"
        const val DELETE_USER = "delete-user"
    }
}