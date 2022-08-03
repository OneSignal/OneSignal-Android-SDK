package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.OneSignal
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.BootstrapOperation
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.Operation
import com.onesignal.onesignal.core.internal.backend.IParamsBackendService
import com.onesignal.onesignal.core.internal.startup.StartupService

class BootstrapExecutor(
    private val _paramsBackendService: IParamsBackendService,
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(BOOTSTRAP)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "BootstrapExecutor(operation: $operation)")

        if(operation !is BootstrapOperation)
            throw Exception("BootstrapExecutor is expecting BootstrapOperation, received $operation")

        _paramsBackendService.fetchAndSaveRemoteParams(operation.appId, operation.subscriptionId)

        // now that we have the params initialized, start everything else up
        // We do *not* make StartupService a dependency on this because if we did, no startable
        // service could depend on the IOperationRepo, which is a likely thing.
        OneSignal.getService<StartupService>().start()
    }

    companion object {
        const val BOOTSTRAP = "bootstrap"
    }
}