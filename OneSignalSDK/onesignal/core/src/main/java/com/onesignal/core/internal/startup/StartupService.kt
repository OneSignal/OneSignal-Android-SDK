package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

internal class StartupService(
    private val services: ServiceProvider,
) {
    private val coroutineScope = CoroutineScope(newSingleThreadContext(name = "StartupService"))

    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach { it.bootstrap() }
    }

    // schedule to start all startable services in a separate thread
    fun scheduleStart() {
        coroutineScope.launch {
            services.getAllServices<IStartableService>().forEach { it.start() }
        }
    }
}
