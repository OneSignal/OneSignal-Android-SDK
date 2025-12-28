package com.onesignal.otel

/**
 * Platform-agnostic crash reporter interface.
 */
internal interface IOtelCrashReporter {
    suspend fun saveCrash(thread: Thread, throwable: Throwable)
}
