package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class UpdatePropertyOperation(
    api: IApiService,
    val id: String,
    val property: String,
    val value: Any?) : BackendOperation(api,"update-user")  {

    override suspend fun executeAsync() {
        Logging.log(LogLevel.DEBUG, "UpdateUserOperation(id: $id, property: $property, value: $value)")

//        api.createUser(null)
    }
}