package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.models.SubscriptionType
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class CreateSubscriptionOperation(
    api: IApiService,
    val id: String,
    val type: SubscriptionType,
    val address: String) : BackendOperation(api,"create-user")  {

    override suspend fun executeAsync() {
        Logging.log(LogLevel.DEBUG, "CreateSubscriptionOperation(id: $id, type: $type, address: $address)")
//        api.createSubscriptionAsync("","", null)
    }
}