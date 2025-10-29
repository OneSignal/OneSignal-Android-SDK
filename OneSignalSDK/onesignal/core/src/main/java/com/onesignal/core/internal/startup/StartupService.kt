package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers

internal class StartupService(
    private val services: ServiceProvider,
) {
    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach { it.bootstrap() }
    }

    // schedule to start all startable services using OneSignal dispatcher
    fun scheduleStart() {
        OneSignalDispatchers.launchOnDefault {
            services.getAllServices<IStartableService>().forEach { it.start() }
        }
    }
}
