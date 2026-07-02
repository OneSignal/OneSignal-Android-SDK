package com.onesignal.internal

import com.onesignal.core.internal.config.ConfigModelStore

/**
 * Owns the lifecycle of the SDK's observability features (remote logging, crash
 * handling, ANR detection) and reacts to remote config changes.
 *
 * Implemented by both [OtelLifecycleManager] (OpenTelemetry path) and
 * [LoggerLifecycleManager] (multiplatform `logger` path) so [OneSignalImp] can switch
 * between them via a single toggle without caring which backend is active.
 */
internal interface IObservabilityLifecycleManager {
    /** Boots whichever features are already enabled from cached config at cold start. */
    fun initializeFromCachedConfig()

    /** Subscribes to config store change events so features react to fresh remote config. */
    fun subscribeToConfigStore(configModelStore: ConfigModelStore)
}
