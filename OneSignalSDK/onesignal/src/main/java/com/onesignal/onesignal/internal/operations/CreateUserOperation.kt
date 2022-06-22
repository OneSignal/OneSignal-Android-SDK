package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class CreateUserOperation(api: IApiService, val id: String) : BackendOperation(api,"create-user")  {
    override suspend fun executeAsync() {
        Logging.log(LogLevel.DEBUG, "CreateUserOperation(id: $id)")
//        api.createUserAsync(null)
    }
}