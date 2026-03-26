package com.onesignal.core.internal.startup

import com.onesignal.common.services.ServiceProvider
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.features.FeatureFlag
import com.onesignal.core.internal.features.IFeatureManager
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
        val useBackgroundThreading =
            try {
                val featureManager = services.getService<IFeatureManager>()
                featureManager.isEnabled(FeatureFlag.SDK_050800_BACKGROUND_THREADING)
            } catch (t: Throwable) {
                Logging.warn("OneSignal: Failed to resolve BACKGROUND_THREADING in StartupService. Falling back to legacy thread.", t)
                false
            }

        if (useBackgroundThreading) {
            OneSignalDispatchers.launchOnDefault {
                services.getAllServices<IStartableService>().forEach { startableService ->
                    try {
                        startableService.start()
                    } catch (t: Throwable) {
                        Logging.error("OneSignal: Startable service failed: ${startableService::class.java.simpleName}", t)
                    }
                }
            }
        } else {
            Thread {
                services.getAllServices<IStartableService>().forEach { startableService ->
                    try {
                        startableService.start()
                    } catch (t: Throwable) {
                        Logging.error("OneSignal: Startable service failed: ${startableService::class.java.simpleName}", t)
                    }
                }
            }.start()
        }
    }
}
