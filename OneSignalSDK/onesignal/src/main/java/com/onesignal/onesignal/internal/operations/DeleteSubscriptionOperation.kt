package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class DeleteSubscriptionOperation(api: IApiService, val id: String) : BackendOperation(api,"delete-user")  {
    override suspend fun executeAsync() {
        Logging.log(LogLevel.DEBUG, "DeleteSubscriptionOperation(id: $id)")

        //api.deleteSubscriptionAsync(id)
    }
}