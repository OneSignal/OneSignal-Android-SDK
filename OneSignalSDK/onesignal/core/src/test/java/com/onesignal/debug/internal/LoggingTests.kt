package com.onesignal.debug.internal

import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith

class TestLogLister : ILogListener {
    val calls = ArrayList<String>()

    override fun onLogEvent(event: OneSignalLogEvent) {
        calls += event.entry
    }
}

infix fun <T : Collection<String>> T.shouldHaveEachItemEndWith(expected: Array<String>): T {
    this.forEachIndexed { index, it -> it shouldEndWith expected[index] }
    return this
}

class LoggingTests : FunSpec({
    // Track every listener registered in a test so we can clear them in afterEach.
    // Without this, leaked listeners survive across test classes in the same JVM and any
    // later test that triggers Logging.warn(msg, throwable) crashes on the unmocked
    // android.util.Log.getStackTraceString call inside callLogListeners.
    val registered = mutableListOf<ILogListener>()
    fun register(listener: ILogListener): ILogListener {
        registered.add(listener)
        Logging.addListener(listener)
        return listener
    }

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    afterEach {
        registered.forEach { Logging.removeListener(it) }
        registered.clear()
    }

    test("addListener") {
        // Given
        val listener = TestLogLister()
        register(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldHaveEachItemEndWith arrayOf("test")
    }

    test("addListener twice") {
        // Given
        val listener = TestLogLister()
        register(listener)
        register(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldHaveEachItemEndWith arrayOf("test")
    }

    test("removeListener") {
        // Given
        val listener = TestLogLister()
        register(listener)
        Logging.removeListener(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldBe arrayOf<String>()
    }

    test("removeListener twice") {
        // Given
        val listener = TestLogLister()
        register(listener)
        Logging.removeListener(listener)
        Logging.removeListener(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldBe arrayOf<String>()
    }

    test("addListener nested") {
        // Given
        val nestedListener = TestLogLister()
        val outerListener = ILogListener { register(nestedListener) }
        register(outerListener)

        // When
        Logging.debug("test")
        Logging.debug("test2")
        Logging.debug("test3")

        // Then
        nestedListener.calls shouldHaveEachItemEndWith arrayOf("test2", "test3")
    }

    test("removeListener nested") {
        // Given
        val calls = ArrayList<String>()
        lateinit var listener: ILogListener
        listener = ILogListener { logEvent ->
            calls += logEvent.entry
            // Remove self from listeners
            Logging.removeListener(listener)
        }
        register(listener)

        // When
        Logging.debug("test")
        Logging.debug("test2")

        // Then
        calls shouldHaveEachItemEndWith arrayOf("test")
    }
})
