package com.onesignal.debug.internal.logging

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.logger.ILogTelemetryRemote
import com.onesignal.logger.LogRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers the single remaining remote-logging sink. Emission is asynchronous, so each
 * assertion waits on the shared scope before verifying.
 */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class LoggingRemoteTest : FunSpec({
    val originalLogLevel = Logging.logLevel

    beforeEach {
        Logging.logLevel = LogLevel.VERBOSE
        Logging.setLoggerTelemetry(null) { false }
    }

    afterEach {
        Logging.logLevel = originalLogLevel
        Logging.setLoggerTelemetry(null) { false }
    }

    test("emits a record to the logger sink when the level is sendable") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val record = slot<LogRecord>()
        val emitted = signalOn(telemetry)
        Logging.setLoggerTelemetry(telemetry) { true }

        Logging.error("boom")
        awaitEmit(emitted)

        coVerify { telemetry.emit(capture(record)) }
        record.captured.body shouldBe "[${Thread.currentThread().name}] boom"
        record.captured.attributes["log.level"] shouldBe "ERROR"
    }

    test("includes exception details when a throwable is supplied") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val record = slot<LogRecord>()
        val emitted = signalOn(telemetry)
        Logging.setLoggerTelemetry(telemetry) { true }

        Logging.error("with cause", IllegalStateException("bad state"))
        awaitEmit(emitted)

        coVerify { telemetry.emit(capture(record)) }
        record.captured.attributes["exception.type"] shouldBe "java.lang.IllegalStateException"
        record.captured.attributes["exception.message"] shouldBe "bad state"
    }

    test("does not emit when the level check rejects the level") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        Logging.setLoggerTelemetry(telemetry) { level -> level <= LogLevel.ERROR }

        Logging.info("filtered out")
        runBlocking { delay(QUIET_WINDOW_MS) }

        coVerify(exactly = 0) { telemetry.emit(any()) }
    }

    test("does not emit NONE level even when the check accepts everything") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        Logging.setLoggerTelemetry(telemetry) { true }

        Logging.log(LogLevel.NONE, "should be dropped")
        runBlocking { delay(QUIET_WINDOW_MS) }

        coVerify(exactly = 0) { telemetry.emit(any()) }
    }

    test("clearing the telemetry stops emission") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        Logging.setLoggerTelemetry(telemetry) { true }
        Logging.setLoggerTelemetry(null) { false }

        Logging.error("after clear")
        runBlocking { delay(QUIET_WINDOW_MS) }

        coVerify(exactly = 0) { telemetry.emit(any()) }
    }

    test("a throwing sink does not propagate to the caller") {
        val telemetry = mockk<ILogTelemetryRemote>()
        val emitted = CompletableDeferred<Unit>()
        coEvery { telemetry.emit(any()) } answers { emitted.complete(Unit); throw RuntimeException("sink down") }
        Logging.setLoggerTelemetry(telemetry) { true }

        Logging.error("survives a broken sink")
        awaitEmit(emitted)

        coVerify { telemetry.emit(any()) }
    }

    test("every severity is forwarded") {
        val telemetry = mockk<ILogTelemetryRemote>(relaxed = true)
        val sixth = CompletableDeferred<Unit>()
        // Emission fans out across Dispatchers.Default, so the counter is touched concurrently.
        val seen = AtomicInteger(0)
        coEvery { telemetry.emit(any()) } answers { if (seen.incrementAndGet() == 6) sixth.complete(Unit); Unit }
        Logging.setLoggerTelemetry(telemetry) { true }

        Logging.verbose("v")
        Logging.debug("d")
        Logging.info("i")
        Logging.warn("w")
        Logging.error("e")
        Logging.fatal("f")
        awaitEmit(sixth)

        coVerify(exactly = 6) { telemetry.emit(any()) }
    }
})

/** Completes when the sink has been reached, so positive cases never race a fixed sleep. */
private fun signalOn(telemetry: ILogTelemetryRemote): CompletableDeferred<Unit> {
    val emitted = CompletableDeferred<Unit>()
    coEvery { telemetry.emit(any()) } answers { emitted.complete(Unit); Unit }
    return emitted
}

private fun awaitEmit(signal: CompletableDeferred<Unit>) {
    runBlocking { withTimeout(EMIT_TIMEOUT_MS) { signal.await() } }
}

/** Generous upper bound on a signal we expect; only a hang burns the full budget. */
private const val EMIT_TIMEOUT_MS = 5_000L

/** Settle window for the negative cases, where there is no event to wait on. */
private const val QUIET_WINDOW_MS = 200L
