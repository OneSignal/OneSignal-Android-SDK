package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

internal class StartupService(
    private val services: ServiceProvider,
) {
    private var coroutineScope = CoroutineScope(newSingleThreadContext(name = "ServiceProvider"))

    fun bootstrap() {
        for (bootstrapService in services.getAllServices<IBootstrapService>()) {
            bootstrapService.bootstrap()
        }
    }

    // schedule to start all startable services in a separate thread
    fun scheduleStart() {
        coroutineScope.launch {
            for (service in services.getAllServices<IStartableService>()) {
                service.start()
            }
        }
    }
}
