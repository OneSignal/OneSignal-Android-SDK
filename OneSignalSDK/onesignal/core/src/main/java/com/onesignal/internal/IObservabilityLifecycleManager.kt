package com.onesignal.internal

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.logger.ISdkEventRecorder

/**
 * Narrow contract over the observability pipeline, so [OneSignalImp] holds it without depending
 * on the backing module.
 */
internal interface IObservabilityLifecycleManager {
    /** Boots whichever features are already enabled from cached config at cold start. */
    fun initializeFromCachedConfig()

    /** Subscribes to config store change events so features react to fresh remote config. */
    fun subscribeToConfigStore(configModelStore: ConfigModelStore)

    /** Attaches [recorder] to the live remote telemetry, if any, and to every one installed afterwards. */
    fun attachEventRecorder(recorder: ISdkEventRecorder)
}
