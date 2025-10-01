package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal class StartupService(
    private val services: ServiceProvider,
) {
    fun bootstrap() {
        services.getAllServices<IBootstrapService>().forEach { it.bootstrap() }
    }

    // schedule to start all startable services in a separate thread
    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleStart() {
        GlobalScope.launch(Dispatchers.Default) {
            services.getAllServices<IStartableService>().forEach { it.start() }
        }
    }
}
