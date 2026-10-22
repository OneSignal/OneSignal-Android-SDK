package com.onesignal.common.threading

import com.onesignal.core.internal.startup.IStartableService

/** Sends a degraded pool choice to remote telemetry, which init installs before services start. */
internal class DispatcherChoiceReporter : IStartableService {
    override fun start() = OneSignalDispatchers.reportChoiceToTelemetry()
}
