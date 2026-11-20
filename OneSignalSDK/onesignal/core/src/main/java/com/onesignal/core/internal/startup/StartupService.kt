package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.debug.internal.logging.Logging

internal class StartupService(
    private val services: ServiceProvider,
) {
    private val bootstrapped = mutableSetOf<IBootstrapService>()
    private val failures = mutableMapOf<IBootstrapService, Exception>()

    // Marked before the call, so a service that subscribes and then throws is not run again. A later
    // attempt still brings the rest up, then fails: reaching SUCCESS without one hides a dead subscriber.
    @Suppress("TooGenericExceptionCaught")
    fun bootstrap() {
        var earlier: Exception? = null
        services.getAllServices<IBootstrapService>().forEach {
            val failed = failures[it]
            if (failed != null) {
                earlier = earlier ?: failed
                return@forEach
            }
            if (it in bootstrapped) return@forEach
            bootstrapped += it
            try {
                it.bootstrap()
            } catch (e: Exception) {
                failures[it] = e
                Logging.warn("OneSignal: ${it.javaClass.simpleName} bootstrap failed and will not be retried: ${e.message}")
                throw e
            }
        }
        earlier?.let { throw IllegalStateException("OneSignal: a bootstrap service failed on an earlier attempt and is not retried", it) }
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
