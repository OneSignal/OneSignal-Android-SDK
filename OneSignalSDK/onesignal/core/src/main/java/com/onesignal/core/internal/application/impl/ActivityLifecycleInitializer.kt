package com.onesignal.core.internal.application.impl

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.onesignal.core.internal.application.IApplicationService

/**
 * Registers activity lifecycle observation at process start via androidx.startup, before the host
 * app initializes the SDK.
 *
 * Wrapper SDKs (Flutter/React Native/Capacitor/etc.) call `initWithContext` late — after the first
 * activity has already been created/started/resumed — so observing the lifecycle only from `start`
 * misses those early callbacks. Registering here lets [ApplicationService] see the first activity's
 * full lifecycle regardless of when init later happens.
 *
 * This intentionally does NOT initialize the SDK or require an App ID; it only attaches lifecycle
 * observation. App ID / consent gating remains in the runtime `initWithContext` path.
 */
class ActivityLifecycleInitializer : Initializer<IApplicationService> {
    override fun create(context: Context): IApplicationService {
        val applicationService = ApplicationService.getInstance()
        (context.applicationContext as? Application)?.let { application ->
            applicationService.attachToApplication(application)
        }
        return applicationService
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
