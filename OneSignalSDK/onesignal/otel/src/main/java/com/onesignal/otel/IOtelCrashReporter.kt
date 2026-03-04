package com.onesignal.otel

/**
 * Platform-agnostic crash reporter interface.
 */
interface IOtelCrashReporter {
    suspend fun saveCrash(thread: Thread, throwable: Throwable)
}
