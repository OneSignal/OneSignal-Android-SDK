package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService

class CreateSubscriptionOperation(api: IApiService) : BackendOperation(api,"create-user")  {
    override suspend fun executeAsync() {
//        api.createSubscriptionAsync("","", null)
    }
}