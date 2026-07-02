package com.onesignal.logger

/**
 * Platform-agnostic crash reporter. Persists a captured crash so it can be shipped
 * on the next launch.
 */
interface ILogCrashReporter {
    suspend fun saveCrash(crash: CrashData)
}

/**
 * Platform-agnostic crash handler. Registration of the native handler is
 * platform-specific (Android: `Thread.UncaughtExceptionHandler`), so the
 * implementation lives in the platform layer; this interface is the contract the
 * lifecycle owner uses.
 */
interface ILogCrashHandler {
    /** Installs the crash handler. Call as early as possible. */
    fun initialize()

    /** Restores the previous handler. Safe to call if never initialized. */
    fun unregister()
}

/**
 * Platform-agnostic ANR (Application Not Responding) detector. ANRs are detected
 * by monitoring main-thread responsiveness, which is platform-specific, so the
 * implementation lives in the platform layer.
 */
interface ILogAnrDetector {
    fun start()

    fun stop()
}
