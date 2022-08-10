package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.*

class PropertyOperationExecutor(
    private val _http: IHttpClient) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(UPDATE_PROPERTY)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "PropertyOperationExecutor(operation: $operation)")

        if(operation is UpdatePropertyOperation) {
            //_api.createUserAsync(null)
        }
    }

    companion object {
        const val UPDATE_PROPERTY = "update-property"
    }
}