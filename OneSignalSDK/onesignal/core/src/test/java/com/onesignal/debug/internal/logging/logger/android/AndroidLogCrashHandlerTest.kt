package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.logger.CrashData
import com.onesignal.logger.ILogCrashReporter
import com.onesignal.logger.ILogger
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Covers the uncaught-handler capture gate, the attribution verdict it reports, and the
 * guarantee that the host's handler still runs. ANRs are captured regardless of fault, so
 * the capture decision and the verdict are separate.
 */
class AndroidLogCrashHandlerTest : FunSpec({

    class RecordingReporter(private val boom: Boolean = false) : ILogCrashReporter {
        val saved = mutableListOf<CrashData>()

        override fun saveCrash(crash: CrashData) {
            saved.add(crash)
            if (boom) throw IllegalStateException("disk full")
        }

        override fun saveNonFatal(crash: CrashData) {
            saved.add(crash)
        }
    }

    class RecordingLogger : ILogger {
        val lines = mutableListOf<String>()

        override fun error(message: String) { lines.add(message) }
        override fun warn(message: String) { lines.add(message) }
        override fun info(message: String) { lines.add(message) }
        override fun debug(message: String) { lines.add(message) }
    }

    fun stackOf(vararg classNames: String): Array<StackTraceElement> =
        classNames.map { StackTraceElement(it, "run", "F.java", 1) }.toTypedArray()

    fun throwableFrom(vararg classNames: String, message: String = "boom"): Throwable =
        RuntimeException(message).apply { stackTrace = stackOf(*classNames) }

    /** Mirrors how watchdog libraries build an ANR error: one synthetic throwable per live thread. */
    fun anrWithThreadDump(
        mainThreadClasses: Array<String>,
        otherThreadClasses: Array<String>,
    ): Throwable {
        val otherThread = RuntimeException("Thread: OneSignal-Log-ANR-Watchdog").apply {
            stackTrace = stackOf(*otherThreadClasses)
        }
        return RuntimeException("Application Not Responding for at least 5000 ms").apply {
            stackTrace = stackOf(*mainThreadClasses)
            initCause(otherThread)
        }
    }

    test("an ANR blocked in third-party code is captured but not reported as ours") {
        val reporter = RecordingReporter()
        val handler = AndroidLogCrashHandler(reporter, RecordingLogger())

        handler.uncaughtException(
            Thread.currentThread(),
            throwableFrom("com.other.app.Thing", message = "Application Not Responding: main blocked"),
        )

        reporter.saved.single().attributed shouldBe false
    }

    test("an ANR thread dump carrying our watchdog is not reported as ours") {
        // The cause chain is a snapshot of every live thread, so our own watchdog appears in it.
        // Only the blocked main thread's stack decides attribution.
        val reporter = RecordingReporter()
        val handler = AndroidLogCrashHandler(reporter, RecordingLogger())

        handler.uncaughtException(
            Thread.currentThread(),
            anrWithThreadDump(
                mainThreadClasses = arrayOf("com.hostapp.ui.MainActivity", "android.os.Looper"),
                otherThreadClasses = arrayOf("com.onesignal.debug.internal.logging.logger.android.AndroidLogAnrDetector"),
            ),
        )

        reporter.saved.single().attributed shouldBe false
    }

    test("an ANR blocked in OneSignal code is reported as ours") {
        val reporter = RecordingReporter()
        val handler = AndroidLogCrashHandler(reporter, RecordingLogger())

        handler.uncaughtException(
            Thread.currentThread(),
            throwableFrom("com.onesignal.Thing", message = "Application Not Responding: main blocked"),
        )

        reporter.saved.single().attributed shouldBe true
    }

    test("a crash whose OneSignal frames are buried in a cause is still reported as ours") {
        val reporter = RecordingReporter()
        val handler = AndroidLogCrashHandler(reporter, RecordingLogger())
        val wrapped = throwableFrom("android.app.ActivityThread").apply {
            initCause(throwableFrom("com.onesignal.core.Thing"))
        }

        handler.uncaughtException(Thread.currentThread(), wrapped)

        reporter.saved.single().attributed shouldBe true
    }

    test("a package merely prefixed with our name is not us") {
        val reporter = RecordingReporter()
        val handler = AndroidLogCrashHandler(reporter, RecordingLogger())

        handler.uncaughtException(Thread.currentThread(), throwableFrom("com.onesignalicious.Ad"))

        reporter.saved.shouldBeEmpty()
    }

    test("a crash with no OneSignal frames is not captured at all") {
        val reporter = RecordingReporter()
        val handler = AndroidLogCrashHandler(reporter, RecordingLogger())

        handler.uncaughtException(Thread.currentThread(), throwableFrom("com.other.app.Thing"))

        reporter.saved.shouldBeEmpty()
    }

    test("the log line records both whether it was an ANR and whether it was attributed") {
        val logger = RecordingLogger()
        val handler = AndroidLogCrashHandler(RecordingReporter(), logger)

        handler.uncaughtException(
            Thread.currentThread(),
            throwableFrom("com.other.app.Thing", message = "Application Not Responding: main blocked"),
        )

        logger.lines.single { it.contains("saving report") } shouldContain "anr=true attributed=false"
    }

    test("the host handler still runs when capture itself blows up") {
        // The logger throws outside the save try/catch, so only the finally keeps delegation alive.
        val delegated = mutableListOf<Throwable>()
        val exploding = object : ILogger {
            override fun error(message: String) = throw IllegalStateException("logger boom")
            override fun warn(message: String) = throw IllegalStateException("logger boom")
            override fun info(message: String) = throw IllegalStateException("logger boom")
            override fun debug(message: String) = throw IllegalStateException("logger boom")
        }
        val handler = AndroidLogCrashHandler(RecordingReporter(), exploding)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> delegated.add(t) }
        try {
            runCatching { handler.initialize() }
            val crash = throwableFrom("com.onesignal.Thing")

            runCatching { handler.uncaughtException(Thread.currentThread(), crash) }

            delegated.single() shouldBe crash
        } finally {
            handler.unregister()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    test("the host handler still runs when saving the report throws") {
        val delegated = mutableListOf<Throwable>()
        val handler = AndroidLogCrashHandler(RecordingReporter(boom = true), RecordingLogger())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> delegated.add(t) }
        try {
            handler.initialize()
            val crash = throwableFrom("com.onesignal.Thing")

            handler.uncaughtException(Thread.currentThread(), crash)

            delegated.single() shouldBe crash
        } finally {
            handler.unregister()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    test("the host handler still runs for a crash we decline to capture") {
        val delegated = mutableListOf<Throwable>()
        val handler = AndroidLogCrashHandler(RecordingReporter(), RecordingLogger())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, t -> delegated.add(t) }
        try {
            handler.initialize()
            val crash = throwableFrom("com.other.app.Thing")

            handler.uncaughtException(Thread.currentThread(), crash)

            delegated.single() shouldBe crash
        } finally {
            handler.unregister()
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
})
