package com.onesignal.onesignal.core.internal.startup

import androidx.annotation.WorkerThread
import com.onesignal.onesignal.core.OneSignal

/**
 * Implement and provide this interface as part of service registration to indicate the service
 * wants to be instantiated and its [start] function called during the initialization process.
 * When the SDK is initialized via [OneSignal.initWithContext], all "startable" services will be
 * processed.
 *
 * When started there is no guarantee that any data is available.  Typically a startable service
 * must be instantiated immediately and will add their appropriate hooks to then respond to changes
 * in the system.
 */
interface IStartableService {

    /**
     * Called when the service is to be started.
     */
    @WorkerThread
    fun start()
}