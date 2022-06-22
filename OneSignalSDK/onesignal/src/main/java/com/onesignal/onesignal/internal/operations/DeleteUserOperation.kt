package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class DeleteUserOperation(api: IApiService, val id: String) : BackendOperation(api,"delete-user")  {
    override suspend fun executeAsync() {
        Logging.log(LogLevel.DEBUG, "DeleteUserOperation(id: $id)")

        //api.deleteUserAsync("external_id", id)
    }
}