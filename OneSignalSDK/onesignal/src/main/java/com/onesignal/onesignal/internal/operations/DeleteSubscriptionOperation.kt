package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService

class DeleteSubscriptionOperation(api: IApiService, val id: String) : BackendOperation(api,"delete-user")  {
    override suspend fun executeAsync() {
        api.deleteSubscriptionAsync(id)
    }
}