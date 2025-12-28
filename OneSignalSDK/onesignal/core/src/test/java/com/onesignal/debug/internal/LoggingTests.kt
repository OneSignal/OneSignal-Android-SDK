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
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("addListener") {
        // Given
        val listener = TestLogLister()
        Logging.addListener(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldHaveEachItemEndWith arrayOf("test")
    }

    test("addListener twice") {
        // Given
        val listener = TestLogLister()
        Logging.addListener(listener)
        Logging.addListener(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldHaveEachItemEndWith arrayOf("test")
    }

    test("removeListener") {
        // Given
        val listener = TestLogLister()
        Logging.addListener(listener)
        Logging.removeListener(listener)

        // When
        Logging.debug("test")

        // Then
        listener.calls shouldBe arrayOf<String>()
    }

    test("removeListener twice") {
        // Given
        val listener = TestLogLister()
        Logging.addListener(listener)
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
        Logging.addListener { Logging.addListener(nestedListener) }

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
        var listener: ILogListener? = null
        listener =
            ILogListener {
                calls += it.entry
                listener?.let { listener -> Logging.removeListener(listener) }
            }
        Logging.addListener(listener)

        // When
        Logging.debug("test")
        Logging.debug("test2")

        // Then
        calls shouldHaveEachItemEndWith arrayOf("test")
    }
})
