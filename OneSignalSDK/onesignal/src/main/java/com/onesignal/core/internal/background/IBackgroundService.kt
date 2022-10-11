package com.onesignal.core.internal.background

import androidx.annotation.WorkerThread

/**
 * Implement and provide this interface as part of service registration to indicate the service
 * wants to be instantiated and its [backgroundRun] function called when the app is in the background.  The
 * background process is initiated when the application is no longer in focus.  Each background
 * service's [scheduleBackgroundRunIn] will be analyzed to determine when [backgroundRun] should be called.
 */
internal interface IBackgroundService {

    /**
     * When this background service should be run, in milliseconds. If null, this service does not
     * need to be run in the background.
     */
    val scheduleBackgroundRunIn: Long?

    /**
     * Run the background service
     */
    @WorkerThread
    suspend fun backgroundRun()
}
