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
})
