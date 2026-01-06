package com.onesignal.debug.internal.crash

/**
 * Constants for ANR (Application Not Responding) detection configuration.
 */
internal object AnrConstants {
    /**
     * Default ANR threshold in milliseconds.
     * Android's default ANR threshold is 5 seconds (5000ms).
     * An ANR is reported when the main thread is unresponsive for this duration.
     */
    const val DEFAULT_ANR_THRESHOLD_MS: Long = 5_000L

    /**
     * Default check interval in milliseconds.
     * The ANR detector checks the main thread responsiveness every 2 seconds.
     */
    const val DEFAULT_CHECK_INTERVAL_MS: Long = 2_000L
}
