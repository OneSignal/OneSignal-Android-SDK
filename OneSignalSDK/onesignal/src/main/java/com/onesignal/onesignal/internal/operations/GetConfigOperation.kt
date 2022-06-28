package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class GetConfigOperation(api: IApiService) : BackendOperation(api,"get-config")  {

    override suspend fun executeAsync() {
        Logging.log(LogLevel.DEBUG, "GetConfigOperation()")
//        api.getParamsAsync(null)
    }
}