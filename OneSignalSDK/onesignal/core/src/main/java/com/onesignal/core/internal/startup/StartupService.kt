package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.CoroutineDispatcherProvider
import com.onesignal.common.threading.DefaultDispatcherProvider

internal class StartupService(
    private val services: ServiceProvider,
    private val dispatchers: CoroutineDispatcherProvider = DefaultDispatcherProvider(),
) {
    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach { it.bootstrap() }
    }

    // schedule to start all startable services using the provided dispatcher
    fun scheduleStart() {
        dispatchers.launchOnDefault {
            services.getAllServices<IStartableService>().forEach { it.start() }
        }
    }
}
