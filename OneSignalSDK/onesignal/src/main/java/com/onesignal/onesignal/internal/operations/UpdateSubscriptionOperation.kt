package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService

class UpdateSubscriptionOperation(api: IApiService, val id: String) : BackendOperation(api,"update-user")  {
    override suspend fun executeAsync() {
        api.updateSubscriptionAsync()
    }
}