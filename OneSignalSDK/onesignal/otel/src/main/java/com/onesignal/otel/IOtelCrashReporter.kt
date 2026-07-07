package com.onesignal.otel

/**
 * Platform-agnostic crash reporter interface.
 */
interface IOtelCrashReporter {
    /**
     * Records a fatal, crash-class event on the retained, disk-buffered crash telemetry
     * (Severity.FATAL). Use for real crashes and foreground ANRs.
     */
    suspend fun saveCrash(thread: Thread, throwable: Throwable)

    /**
     * Records a non-fatal event on the same retained, disk-buffered crash telemetry, but at
     * Severity.WARN and tagged as non-fatal, so it stays out of any severity-based crash/ANR metric
     * while remaining queryable. Use for backgrounded main-thread blocks and other retained warnings
     * that are not user-visible crashes.
     */
    suspend fun saveNonFatal(thread: Thread, throwable: Throwable)
}
