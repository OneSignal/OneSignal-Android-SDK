package com.onesignal.otel.crash

/**
 * Platform-agnostic interface for ANR (Application Not Responding) detection.
 *
 * ANRs occur when the main thread is blocked for too long (typically >5 seconds on Android).
 * Unlike crashes, ANRs don't throw exceptions - they're detected by monitoring thread responsiveness.
 */
interface IOtelAnrDetector {
    /**
     * Starts monitoring for ANRs.
     * This should be called early in the app lifecycle, ideally right after crash handler initialization.
     */
    fun start()

    /**
     * Stops monitoring for ANRs.
     * Should be called when the app is shutting down or when monitoring is no longer needed.
     */
    fun stop()
}
