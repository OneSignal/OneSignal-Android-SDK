package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.debug.internal.logging.Logging

internal class StartupService(
    private val services: ServiceProvider,
) {
    private val bootstrapped = mutableSetOf<IBootstrapService>()

    // Marked before the call, so a service that subscribes and then throws is not run again.
    @Suppress("TooGenericExceptionCaught")
    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach {
            if (it in bootstrapped) return@forEach
            bootstrapped += it
            try {
                it.bootstrap()
            } catch (e: Exception) {
                Logging.warn("OneSignal: ${it.javaClass.simpleName} bootstrap failed and will not be retried: ${e.message}")
                throw e
            }
        }
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
