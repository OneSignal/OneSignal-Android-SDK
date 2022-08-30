package com.onesignal.onesignal.core.internal.background

/**
 * The background manager is used by a service to run all services registered as
 * [IBackgroundService].  It should not be called for any other purpose.
 */
interface IBackgroundManager {
    /**
     * Whether the service should reschedule itself to re-run.
     */
    var needsJobReschedule: Boolean

    /**
     * Run the background services.
     */
    suspend fun runBackgroundServices()

    /**
     * Cancel the running of the background services.
     */
    fun cancelRunBackgroundServices(): Boolean
}
