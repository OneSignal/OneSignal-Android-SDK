package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.internal.backend.api.IApiService
import com.onesignal.onesignal.core.internal.logging.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.*

class ConfigOperationExecutor(
    private val _api: IApiService) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(GET_CONFIG)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "PropertyOperationExecutor(operation: $operation)")

        if(operation is GetConfigOperation) {
            _api.getParamsAsync()
        }
    }

    companion object {
        const val GET_CONFIG = "get-config"
    }
}