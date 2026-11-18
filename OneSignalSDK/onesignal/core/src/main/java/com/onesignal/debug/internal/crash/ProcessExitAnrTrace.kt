package com.onesignal.debug.internal.crash

import com.onesignal.logger.CrashData

/**
 * Parses the system ANR dump the OS attaches to a process-exit record. Pure text handling, kept
 * apart from the Android reader so every shape below is testable without a device.
 */
internal object ProcessExitAnrTrace {
    /** Thread block header, e.g. `"main" prio=5 tid=1 Native`. */
    private val THREAD_HEADER = Regex("^\"(.*?)\"")

    /** A stack frame line, e.g. `  at com.onesignal.Foo.bar(Foo.kt:1)`. */
    private val FRAME = Regex("^\\s+at\\s+(\\S.*)$")

    private const val MAIN_THREAD = "main"
    private const val ONESIGNAL_PACKAGE_PREFIX = "com.onesignal"

    /** Frames of the thread named [MAIN_THREAD], in dump order. Empty when the dump has no main block. */
    fun mainThreadFrames(dump: String): List<String> {
        val lines = dump.lines()
        val mainHeader = lines.indexOfFirst { threadNameOf(it) == MAIN_THREAD }
        if (mainHeader < 0) return emptyList()
        return lines.asSequence()
            .drop(mainHeader + 1)
            // The OS dumps every thread, so the next header is the end of main's frames.
            .takeWhile { threadNameOf(it) == null }
            .mapNotNull { FRAME.find(it)?.groupValues?.get(1)?.trim() }
            .toList()
    }

    private fun threadNameOf(line: String): String? = THREAD_HEADER.find(line)?.groupValues?.get(1)

    /**
     * Whether a OneSignal frame is on the blocked main thread. Unlike the live watchdog's
     * predicate there is no handler of ours in this dump: the OS produced it, so any frame of
     * ours was genuinely on the stack while the thread was blocked.
     */
    fun isOneSignalAtFault(frames: List<String>): Boolean = frames.any { it.startsWith(ONESIGNAL_PACKAGE_PREFIX) }

    /**
     * Builds the record for an ANR the OS killed us for. Keeps [ANR_EXCEPTION_TYPE] so these
     * group with live ANRs on dashboards keyed to it; the message is what tells them apart.
     */
    fun buildCrashData(
        frames: List<String>,
        appState: String,
        occurredAtMs: Long,
    ): CrashData {
        val message = POST_MORTEM_MESSAGE
        return CrashData(
            threadName = MAIN_THREAD,
            exceptionType = ANR_EXCEPTION_TYPE,
            exceptionMessage = message,
            stacktrace = formatFrames(ANR_EXCEPTION_TYPE, message, frames),
            attributed = isOneSignalAtFault(frames),
            appState = appState,
            occurredAtMs = occurredAtMs,
        )
    }

    /** Must match [formatJvmStacktrace]: consumers parse `exception.stacktrace` the same way for both. */
    private fun formatFrames(
        exceptionType: String,
        exceptionMessage: String,
        frames: List<String>,
    ): String {
        val lineSeparator = System.lineSeparator()
        return buildString {
            append(exceptionType)
            if (exceptionMessage.isNotEmpty()) {
                append(": ").append(exceptionMessage)
            }
            append(lineSeparator)
            for (frame in frames) {
                append("\tat ").append(frame).append(lineSeparator)
            }
        }
    }

    const val POST_MORTEM_MESSAGE = "Application Not Responding: process killed by the system, detected on the next launch"
}
