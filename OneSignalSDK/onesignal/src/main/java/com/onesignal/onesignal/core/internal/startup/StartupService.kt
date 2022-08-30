package com.onesignal.onesignal.core.internal.startup

internal class StartupService(
    private val _startableServices: List<IStartableService>,
) {
    fun start() {
        // now that we have the params initialized, start everything else up
        for (startableService in _startableServices)
            startableService.start()
    }
}
