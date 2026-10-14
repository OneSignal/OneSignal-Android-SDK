package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IConnectivityMonitor
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ends the retry backoff of queued operations when the app comes to the foreground, and when the
 * device comes back online while it is in the foreground. Network changes in the background are
 * left to the timed retry or the next foreground.
 */
internal class OperationRetryTrigger(
    private val applicationService: IApplicationService,
    private val connectivityMonitor: IConnectivityMonitor,
    private val operationRepo: IOperationRepo,
) : IStartableService, IApplicationLifecycleHandler {
    private val monitoring = AtomicBoolean(false)

    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
        if (monitoring.compareAndSet(false, true)) {
            connectivityMonitor.addOnlineHandler {
                if (applicationService.isInForeground) operationRepo.forceExecuteOperations()
            }
        }
        if (firedOnSubscribe) return
        operationRepo.forceExecuteOperations()
    }

    override fun onUnfocused() {}
}
