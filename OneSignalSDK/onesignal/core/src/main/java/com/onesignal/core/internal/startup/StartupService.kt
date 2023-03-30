package com.onesignal.core.internal.startup

internal class StartupService(
    private val _bootstrapServices: List<IBootstrapService>,
    private val _startableServices: List<IStartableService>,
) {
    fun bootstrap() {
        // now that we have the params initialized, start everything else up
        for (bootstrapService in _bootstrapServices)
            bootstrapService.bootstrap()
    }

    fun start() {
        // now that we have the params initialized, start everything else up
        for (startableService in _startableServices)
            startableService.start()
    }
}
