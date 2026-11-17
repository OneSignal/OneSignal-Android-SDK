package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.logger.CrashData
import com.onesignal.logger.ILogCrashHandler
import com.onesignal.logger.ILogCrashReporter
import com.onesignal.logger.ILogger

/**
 * Android [ILogCrashHandler]: installs a [Thread.UncaughtExceptionHandler] and hands OneSignal
 * crashes to [ILogCrashReporter] as platform-neutral [CrashData].
 */
internal class AndroidLogCrashHandler(
    private val crashReporter: ILogCrashReporter,
    private val logger: ILogger,
) : Thread.UncaughtExceptionHandler, ILogCrashHandler {
    private var existingHandler: Thread.UncaughtExceptionHandler? = null
    private val seenThrowables: MutableList<Throwable> = mutableListOf()

    @Volatile
    private var initialized = false

    override fun initialize() {
        if (initialized) {
            logger.warn("AndroidLogCrashHandler already initialized, skipping")
            return
        }
        existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        initialized = true
        logger.info("AndroidLogCrashHandler: registered as default uncaught exception handler")
    }

    override fun unregister() {
        if (!initialized) {
            logger.debug("AndroidLogCrashHandler: not initialized, nothing to unregister")
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(existingHandler)
        existingHandler = null
        initialized = false
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        synchronized(seenThrowables) {
            if (seenThrowables.contains(throwable)) {
                logger.warn("AndroidLogCrashHandler: ignoring duplicate throwable instance")
                return
            }
            seenThrowables.add(throwable)
        }

        try {
            capture(thread, throwable)
        } finally {
            // Must run even if capture blew up, or the host's own reporter loses the crash.
            existingHandler?.uncaughtException(thread, throwable)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun capture(thread: Thread, throwable: Throwable) {
        val isAnr =
            throwable.javaClass.simpleName.contains("ApplicationNotResponding", ignoreCase = true) ||
                throwable.message?.contains("Application Not Responding", ignoreCase = true) == true

        val atFault =
            try {
                attributionOf(throwable, isAnr)
            } catch (t: Throwable) {
                // Walking the graph allocates while the process is dying. Unproven is the safe answer.
                logger.debug("AndroidLogCrashHandler: attribution failed (${t.javaClass.simpleName}), treating as unproven")
                false
            }

        if (!isAnr && !atFault) {
            logger.debug("AndroidLogCrashHandler: crash not OneSignal-related, delegating")
            return
        }

        logger.info("AndroidLogCrashHandler: saving report (anr=$isAnr attributed=$atFault)")
        try {
            // Synchronous by contract — process is about to die; write must finish first.
            crashReporter.saveCrash(throwable.toCrashData(thread, attributed = atFault))
        } catch (t: Throwable) {
            logger.error("AndroidLogCrashHandler: failed to save crash report: ${t.message}")
        }
    }
}

/**
 * Only the blocked thread is evidence for an ANR. Its cause chain is a snapshot of every live
 * thread, so walking it would match our own watchdog and call every third-party ANR ours.
 * Watchdog errors carry an empty root stack and hang the blocked thread off the first cause.
 * Non-ANR crashes still need the walk, since framework wrappers bury real frames in a cause.
 */
internal fun attributionOf(throwable: Throwable, isAnr: Boolean): Boolean {
    if (!isAnr) return isOneSignalAtFault(throwable)
    val blocked = throwable.stackTrace.takeIf { it.isNotEmpty() } ?: throwable.cause?.stackTrace
    return blocked != null && isOneSignalAtFault(blocked)
}

internal fun Throwable.toCrashData(thread: Thread, attributed: Boolean): CrashData =
    CrashData(
        threadName = thread.name,
        exceptionType = this::class.java.name,
        exceptionMessage = message ?: "",
        stacktrace = stackTraceToString(),
        attributed = attributed,
    )

// Trailing dot is load-bearing: without it a third-party `com.onesignalicious` package reads as ours.
internal const val ONESIGNAL_PACKAGE_PREFIX = "com.onesignal."

/** True when any frame originates from OneSignal SDK code. */
internal fun isOneSignalAtFault(stackTrace: Array<StackTraceElement>): Boolean =
    stackTrace.any { it.className.startsWith(ONESIGNAL_PACKAGE_PREFIX) }

/**
 * True when OneSignal appears anywhere in the throwable graph: framework wrappers bury the real
 * frames in a cause. The visited set must stay identity-based — cause chains can be cyclic.
 */
internal fun isOneSignalAtFault(throwable: Throwable): Boolean {
    val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
    val queue = ArrayDeque<Throwable>()
    queue.add(throwable)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (!visited.add(current)) continue
        if (isOneSignalAtFault(current.stackTrace)) return true
        current.cause?.let { queue.add(it) }
        current.suppressed.forEach { queue.add(it) }
    }
    return false
}
