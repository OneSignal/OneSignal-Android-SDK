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

    /**
     * Threshold for main-thread blocks while the app is backgrounded.
     *
     * Android only raises a real, user-visible ANR while the app is in the foreground, so a
     * backgrounded block is not an ANR — it is recorded as a lower-severity warning instead.
     * A higher threshold than the foreground one absorbs the normal scheduling delays a
     * backgrounded process sees, keeping the warning stream meaningful rather than noisy.
     */
    const val DEFAULT_BACKGROUND_BLOCK_THRESHOLD_MS: Long = 10_000L
}
