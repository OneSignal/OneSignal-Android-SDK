package com.onesignal.core.internal.time

/**
 * Provides an abstraction to retrieving the current time.  This should be used rather
 * than standard library services, to allow for more flexible testing scenarios.
 */
interface ITime {
    /**
     * Returns the current time in unix time milliseconds (the number of milliseconds between the
     * current time and midnight, January 1, 1970 UTC).
     */
    val currentTimeMillis: Long

    /**
     * Returns how long the app has been running.
     */
    val processUptimeMillis: Long
}
