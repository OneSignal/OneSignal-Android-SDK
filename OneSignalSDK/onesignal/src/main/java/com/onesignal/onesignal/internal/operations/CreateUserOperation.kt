package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService

class CreateUserOperation(api: IApiService) : BackendOperation(api,"create-user")  {
    override suspend fun executeAsync() {
//        api.createUserAsync(null)
    }
}