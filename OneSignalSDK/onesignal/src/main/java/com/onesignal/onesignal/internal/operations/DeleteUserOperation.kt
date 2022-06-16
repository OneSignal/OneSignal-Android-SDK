package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService

class DeleteUserOperation(api: IApiService, val id: String) : BackendOperation(api,"delete-user")  {
    override suspend fun executeAsync() {
        api.deleteUserAsync("external_id", id)
    }
}