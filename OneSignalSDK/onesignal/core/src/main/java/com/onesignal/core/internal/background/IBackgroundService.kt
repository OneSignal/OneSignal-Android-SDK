package com.onesignal.core.internal.background

import androidx.annotation.WorkerThread

/**
 * Implement and provide this interface as part of service registration to indicate the service
 * wants to be instantiated and its [backgroundRun] function called when the app is in the background.
 * Each background service's [scheduleBackgroundRunIn] will be analyzed to determine when
 * [backgroundRun] should be called.
 */
interface IBackgroundService {
    /**
     * When this background service should be run, in milliseconds. If null, this service does not
     * need to be run in the background.
     */
    val scheduleBackgroundRunIn: Long?

    /**
     * Run the background service.
     * WARNING: This may not follow your scheduleBackgroundRunIn schedule:
     *       1. May run more often as the lowest scheduleBackgroundRunIn
     *       value is used across the SDK.
     *       2. Android doesn't guarantee exact timing on when the job is run,
     *       so it's possible for it to be delayed by a few minutes.
     */
    @WorkerThread
    suspend fun backgroundRun()
}
