package com.onesignal.common.threading

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThreadUtilsFeatureFlagTests : FunSpec({
    beforeEach {
        ThreadingMode.useBackgroundThreading = false
    }

    afterEach {
        unmockkObject(OneSignalDispatchers)
        ThreadingMode.useBackgroundThreading = false
    }

    test("launchOnIO uses OneSignalDispatchers when BACKGROUND_THREADING is on") {
        // Given
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        val expectedJob = mockk<Job>(relaxed = true)
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns expectedJob

        // When
        val actualJob = launchOnIO {}

        // Then
        actualJob shouldBe expectedJob
        verify(exactly = 1) { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) }
    }

    test("launchOnIO avoids OneSignalDispatchers when BACKGROUND_THREADING is off") {
        // Given
        ThreadingMode.useBackgroundThreading = false
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns mockk(relaxed = true)
        val completed = CompletableDeferred<Unit>()

        // When
        val job = launchOnIO { completed.complete(Unit) }

        // Then
        runBlocking { job.join() }
        completed.isCompleted shouldBe true
        verify(exactly = 0) { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) }
    }

    test("launchOnDefault uses OneSignalDispatchers when BACKGROUND_THREADING is on") {
        // Given
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        val expectedJob = mockk<Job>(relaxed = true)
        every { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) } returns expectedJob

        // When
        val actualJob = launchOnDefault {}

        // Then
        actualJob shouldBe expectedJob
        verify(exactly = 1) { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) }
    }

    test("launchOnDefault avoids OneSignalDispatchers when BACKGROUND_THREADING is off") {
        // Given
        ThreadingMode.useBackgroundThreading = false
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) } returns mockk(relaxed = true)
        val completed = CompletableDeferred<Unit>()

        // When
        val job = launchOnDefault { completed.complete(Unit) }

        // Then
        runBlocking { job.join() }
        completed.isCompleted shouldBe true
        verify(exactly = 0) { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) }
    }

    test("suspendifyOnSerialIO always routes through OneSignalDispatchers.launchOnSerialIO regardless of BACKGROUND_THREADING") {
        // suspendifyOnSerialIO intentionally ignores the FF: the serial ordering guarantee
        // is the whole point of this entry point, and the single low-priority daemon thread
        // carries none of the resource concerns the FF gates. Exercise both FF positions in
        // one test to lock in that contract.
        listOf(false, true).forEach { ffOn ->
            ThreadingMode.useBackgroundThreading = ffOn
            mockkObject(OneSignalDispatchers)
            every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

            suspendifyOnSerialIO { }

            verify(exactly = 1) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
            unmockkObject(OneSignalDispatchers)
        }
    }

    test("suspendifyOnSerialIO swallows exceptions thrown inside the block") {
        // Production contract: any exception in the dispatched block is logged and absorbed
        // rather than propagated to the SerialIO thread, so a single misbehaving caller
        // can't kill the dispatcher for the rest of the SDK.
        var ranBlock = false

        suspendifyOnSerialIO {
            ranBlock = true
            throw RuntimeException("intentional")
        }

        // Drain the SerialIO worker: submit a follow-up task and wait for it. If exception
        // handling worked the block above ran and the follow-up runs too.
        val latch = CountDownLatch(1)
        suspendifyOnSerialIO { latch.countDown() }
        latch.await(2, TimeUnit.SECONDS) shouldBe true
        ranBlock shouldBe true
    }
})
