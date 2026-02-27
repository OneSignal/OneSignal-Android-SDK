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

    /**
     * Unregisters this crash handler, restoring the previous default handler.
     * Safe to call even if [initialize] was never called (no-op in that case).
     */
    fun unregister()
}
