package com.onesignal.common.threading

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Job
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThreadUtilsDispatchTests : FunSpec({
    afterEach {
        unmockkObject(OneSignalDispatchers)
    }

    test("launchOnIO routes through OneSignalDispatchers.launchOnIO") {
        mockkObject(OneSignalDispatchers)
        val expectedJob = mockk<Job>(relaxed = true)
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns expectedJob

        val actualJob = launchOnIO {}

        actualJob shouldBe expectedJob
        verify(exactly = 1) { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) }
    }

    test("launchOnDefault routes through OneSignalDispatchers.launchOnDefault") {
        mockkObject(OneSignalDispatchers)
        val expectedJob = mockk<Job>(relaxed = true)
        every { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) } returns expectedJob

        val actualJob = launchOnDefault {}

        actualJob shouldBe expectedJob
        verify(exactly = 1) { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) }
    }

    test("suspendifyOnSerialIO routes through OneSignalDispatchers.launchOnSerialIO") {
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        suspendifyOnSerialIO { }

        verify(exactly = 1) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
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

    test("runOnSerialIO routes through launchOnSerialIO") {
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)
        var ranInline = false

        runOnSerialIO { ranInline = true }

        // Dispatch is deferred to the (mocked) serial dispatcher, so the block does not run inline.
        ranInline shouldBe false
        verify(exactly = 1) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
    }
})
