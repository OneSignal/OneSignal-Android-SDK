package com.onesignal.otel

/**
 * Platform-agnostic crash handler interface.
 * This should be initialized as early as possible and be independent of service architecture.
 */
interface IOtelCrashHandler {
    /**
     * Initialize the crash handler. This should be called as early as possible,
     * before any other initialization that might crash.
     */
    fun initialize()
}
