package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.debug.internal.logging.Logging

internal class StartupService(
    private val services: ServiceProvider,
) {
    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach { it.bootstrap() }
    }

    // schedule to start all startable services in a separate thread
    fun scheduleStart() {
        Thread {
            services.getAllServices<IStartableService>().forEach { it.start() }
            Logging.debug("Crash data: scheduleStart finished")
        }.start()
    }
}
