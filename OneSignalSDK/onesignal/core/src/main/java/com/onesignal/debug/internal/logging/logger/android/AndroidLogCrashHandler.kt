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

    @Suppress("TooGenericExceptionCaught")
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        synchronized(seenThrowables) {
            if (seenThrowables.contains(throwable)) {
                logger.warn("AndroidLogCrashHandler: ignoring duplicate throwable instance")
                return
            }
            seenThrowables.add(throwable)
        }

        val isAnr =
            throwable.javaClass.simpleName.contains("ApplicationNotResponding", ignoreCase = true) ||
                throwable.message?.contains("Application Not Responding", ignoreCase = true) == true

        if (!isAnr && !isOneSignalAtFault(throwable)) {
            logger.debug("AndroidLogCrashHandler: crash not OneSignal-related, delegating")
            existingHandler?.uncaughtException(thread, throwable)
            return
        }

        logger.info("AndroidLogCrashHandler: OneSignal-related crash detected, saving report")
        try {
            // Synchronous by contract — process is about to die; write must finish first.
            crashReporter.saveCrash(throwable.toCrashData(thread))
        } catch (t: Throwable) {
            logger.error("AndroidLogCrashHandler: failed to save crash report: ${t.message}")
        }
        existingHandler?.uncaughtException(thread, throwable)
    }
}

internal fun Throwable.toCrashData(thread: Thread): CrashData =
    CrashData(
        threadName = thread.name,
        exceptionType = this::class.java.name,
        exceptionMessage = message ?: "",
        stacktrace = stackTraceToString(),
    )

/** True when any frame originates from OneSignal SDK code. */
internal fun isOneSignalAtFault(stackTrace: Array<StackTraceElement>): Boolean =
    stackTrace.any { it.className.startsWith("com.onesignal") }

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
