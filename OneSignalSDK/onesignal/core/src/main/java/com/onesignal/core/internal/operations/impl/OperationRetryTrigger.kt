package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService

/**
 * Ends the retry backoff of queued operations when the app comes to the foreground.
 */
internal class OperationRetryTrigger(
    private val applicationService: IApplicationService,
    private val operationRepo: IOperationRepo,
) : IStartableService, IApplicationLifecycleHandler {
    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
        if (firedOnSubscribe) return
        operationRepo.forceExecuteOperations()
    }

    override fun onUnfocused() {}
}
