package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.debug.internal.logging.Logging

internal class StartupService(
    private val services: ServiceProvider,
) {
    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach { it.bootstrap() }
    }

    // schedule to start all startable services using OneSignal dispatcher
    @Suppress("TooGenericExceptionCaught")
    fun scheduleStart() {
        OneSignalDispatchers.launchOnDefault {
            services.getAllServices<IStartableService>().forEach { startableService ->
                try {
                    startableService.start()
                } catch (t: Throwable) {
                    Logging.error("OneSignal: Startable service failed: ${startableService::class.java.simpleName}", t)
                }
            }
        }
    }
}
