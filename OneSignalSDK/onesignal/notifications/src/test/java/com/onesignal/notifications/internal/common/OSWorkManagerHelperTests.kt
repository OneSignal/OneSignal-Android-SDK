package com.onesignal.notifications.internal.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList

private const val TELEMETRY_TOKEN = "sdk_workmanager_init"
private const val UNINITIALIZED = "WorkManager is not initialized properly."

// androidx.work 2.8.1 throws this from the WorkManagerImpl constructor before first unlock.
private const val DIRECT_BOOT = "Cannot initialize WorkManager in direct boot mode"

@RobolectricTest
class OSWorkManagerHelperTests : FunSpec({
    lateinit var context: Context
    lateinit var workManager: WorkManager
    lateinit var events: CopyOnWriteArrayList<OneSignalLogEvent>
    lateinit var listener: ILogListener

    beforeAny {
        context = ApplicationProvider.getApplicationContext()
        workManager = mockk(relaxed = true)
        events = CopyOnWriteArrayList()
        listener = ILogListener { events.add(it) }
        Logging.addListener(listener)
        mockkStatic(WorkManager::class)
    }

    afterAny {
        Logging.removeListener(listener)
        unmockkStatic(WorkManager::class)
    }

    // Scoped to the token: the listener is global, so unrelated SDK logging must not fail these.
    fun reported(level: LogLevel) = events.filter { it.level == level && TELEMETRY_TOKEN in it.entry }

    test("an initialized WorkManager is returned without initializing it again") {
        every { WorkManager.getInstance(any()) } returns workManager

        OSWorkManagerHelper.getInstance(context) shouldBe workManager

        verify(exactly = 0) { WorkManager.initialize(any(), any()) }
        reported(LogLevel.WARN).shouldBeEmpty()
        reported(LogLevel.ERROR).shouldBeEmpty()
    }

    test("an uninitialized WorkManager is initialized, reported at warn, and then returned") {
        var initialized = false
        every { WorkManager.getInstance(any()) } answers {
            if (initialized) workManager else throw IllegalStateException(UNINITIALIZED)
        }
        every { WorkManager.initialize(any(), any()) } answers { initialized = true }

        OSWorkManagerHelper.getInstance(context) shouldBe workManager

        verify(exactly = 1) { WorkManager.initialize(any(), any()) }
        // Saved queries predate the token and match on the old wording.
        reported(LogLevel.WARN).single().entry shouldContain "OSWorkManagerHelper.getInstance failed"
        reported(LogLevel.ERROR).shouldBeEmpty()
    }

    test("losing the initialize race is reported at warn, not error") {
        var initialized = false
        every { WorkManager.getInstance(any()) } answers {
            if (initialized) workManager else throw IllegalStateException(UNINITIALIZED)
        }
        every { WorkManager.initialize(any(), any()) } answers {
            initialized = true
            throw IllegalStateException("WorkManager is already initialized.")
        }

        OSWorkManagerHelper.getInstance(context) shouldBe workManager

        reported(LogLevel.WARN).map { it.entry }.any { "initializing WorkManager failed" in it } shouldBe true
        reported(LogLevel.ERROR).shouldBeEmpty()
    }

    test("both the first failure and the direct boot failure ride along") {
        val initFailure = IllegalStateException(DIRECT_BOOT)
        // WorkManagerImpl builds a new exception per call, so the first failure and the retry differ.
        every { WorkManager.getInstance(any()) } answers { throw IllegalStateException(UNINITIALIZED) }
        every { WorkManager.initialize(any(), any()) } throws initFailure

        val thrown = shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

        thrown.suppressed.toList() shouldContain initFailure
        thrown.suppressed.mapNotNull { it.message }.any { "not initialized properly" in it } shouldBe true
        thrown.cause!!.message shouldContain "not initialized properly"
        thrown.message shouldContain DIRECT_BOOT
    }

    test("a failed recovery is logged at error with the cause under the telemetry token") {
        every { WorkManager.getInstance(any()) } throws IllegalStateException(UNINITIALIZED)
        every { WorkManager.initialize(any(), any()) } throws IllegalStateException(DIRECT_BOOT)

        shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

        val reported = reported(LogLevel.ERROR).single()
        reported.entry shouldContain "$TELEMETRY_TOKEN: OSWorkManagerHelper recovery failed"
        reported.entry shouldContain DIRECT_BOOT
    }

    test("an initialize that reports success but leaves WorkManager unusable is still reported") {
        // WorkManagerImpl builds a new exception per call, so the retry is never the same object.
        every { WorkManager.getInstance(any()) } answers { throw IllegalStateException(UNINITIALIZED) }
        every { WorkManager.initialize(any(), any()) } answers { }

        val thrown = shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

        thrown.suppressed.single().message shouldContain "not initialized properly"
        thrown.cause!!.message shouldContain "not initialized properly"
        reported(LogLevel.ERROR).single().entry shouldContain "$TELEMETRY_TOKEN: OSWorkManagerHelper recovery failed"
    }

    test("a getInstance that keeps rethrowing one exception is not suppressed into itself") {
        every { WorkManager.getInstance(any()) } throws IllegalStateException(UNINITIALIZED)
        every { WorkManager.initialize(any(), any()) } answers { }

        val thrown = shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

        thrown.suppressed.shouldBeEmpty()
    }

    test("the failure names OneSignal so crash grouping keys on the message") {
        // Without "onesignal" in the message, grouping falls back to a frame R8 has renamed.
        every { WorkManager.getInstance(any()) } answers { throw IllegalStateException(UNINITIALIZED) }
        every { WorkManager.initialize(any(), any()) } throws IllegalStateException(DIRECT_BOOT)

        val thrown = shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

        thrown.message!!.lowercase() shouldContain "onesignal"
    }

    test("a log listener that throws first still leaves the record for the others") {
        val thrower = ILogListener { throw IllegalArgumentException("listener blew up") }
        // Registered before the recorder: a listener fault must not abort the ones behind it.
        Logging.removeListener(listener)
        Logging.addListener(thrower)
        Logging.addListener(listener)
        every { WorkManager.getInstance(any()) } answers { throw IllegalStateException(UNINITIALIZED) }
        every { WorkManager.initialize(any(), any()) } throws IllegalStateException(DIRECT_BOOT)

        try {
            shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

            reported(LogLevel.ERROR).single().entry shouldContain "recovery failed"
        } finally {
            Logging.removeListener(thrower)
        }
    }

    test("a throwing log listener does not replace the WorkManager failure") {
        val thrower = ILogListener { throw IllegalArgumentException("listener blew up") }
        Logging.addListener(thrower)
        every { WorkManager.getInstance(any()) } throws IllegalStateException(UNINITIALIZED)
        every { WorkManager.initialize(any(), any()) } throws IllegalStateException(DIRECT_BOOT)

        try {
            val thrown = shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(context) }

            thrown.cause!!.message shouldContain "not initialized properly"
        } finally {
            Logging.removeListener(thrower)
        }
    }

    test("the host's own WorkManager configuration is reused when it has one") {
        val hostConfiguration = Configuration.Builder().build()
        val hostContext =
            mockk<Context> {
                every { applicationContext } returns
                    mockk<HostApplication> {
                        every { workManagerConfiguration } returns hostConfiguration
                    }
            }
        // A Configuration.Provider host reaches this helper through direct boot, which fails both calls.
        every { WorkManager.getInstance(any()) } answers { throw IllegalStateException(DIRECT_BOOT) }
        every { WorkManager.initialize(any(), any()) } throws IllegalStateException(DIRECT_BOOT)

        shouldThrow<WorkManagerUnavailableException> { OSWorkManagerHelper.getInstance(hostContext) }

        verify(exactly = 1) { WorkManager.initialize(any(), hostConfiguration) }
    }
})

/** A host app supplying its own WorkManager configuration, such as one with a Hilt worker factory. */
private abstract class HostApplication : Context(), Configuration.Provider
