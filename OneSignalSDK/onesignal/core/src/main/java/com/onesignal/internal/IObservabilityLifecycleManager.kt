package com.onesignal.internal

import com.onesignal.core.internal.config.ConfigModelStore

/**
 * Owns the lifecycle of the SDK's observability features (remote logging, crash
 * handling, ANR detection) and reacts to remote config changes.
 *
 * Implemented by [LoggerLifecycleManager] so [OneSignalImp] can hold the pipeline
 * behind a narrow contract without depending on the backing module.
 */
internal interface IObservabilityLifecycleManager {
    /** Boots whichever features are already enabled from cached config at cold start. */
    fun initializeFromCachedConfig()

    /** Subscribes to config store change events so features react to fresh remote config. */
    fun subscribeToConfigStore(configModelStore: ConfigModelStore)
}
