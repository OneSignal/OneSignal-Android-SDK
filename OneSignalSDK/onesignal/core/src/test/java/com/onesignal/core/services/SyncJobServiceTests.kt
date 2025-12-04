package com.onesignal.core.services

import android.app.job.JobParameters
import com.onesignal.OneSignal
import com.onesignal.core.internal.background.IBackgroundManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify

private class Mocks {
    val syncJobService = spyk(SyncJobService(), recordPrivateCalls = true)
    val jobParameters = mockk<JobParameters>(relaxed = true)
    val mockBackgroundManager = mockk<IBackgroundManager>(relaxed = true)
}

class SyncJobServiceTests : FunSpec({
    lateinit var mocks: Mocks

    listener(IOMockHelper)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        mocks = Mocks() // fresh instance for each test
        mockkObject(OneSignal)
        every { OneSignal.getService<IBackgroundManager>() } returns mocks.mockBackgroundManager
    }

    afterAny {
        unmockkAll()
    }

    test("onStartJob returns true when initWithContext fails") {
        // Given
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.initWithContext(any()) } returns false

        // When
        val result = syncJobService.onStartJob(jobParameters)

        // Then
        result shouldBe true
    }

    test("onStartJob calls runBackgroundServices when initWithContext succeeds") {
        // Given
        val mockBackgroundManager = mocks.mockBackgroundManager
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { mockBackgroundManager.needsJobReschedule } returns false

        // When
        val result = syncJobService.onStartJob(jobParameters)
        awaitIO()

        // Then
        result shouldBe true
        coVerify { mockBackgroundManager.runBackgroundServices() }
        verify { syncJobService.jobFinished(jobParameters, false) }
    }

    test("onStartJob calls jobFinished with false when initWithContext failed") {
        // Given
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.initWithContext(any()) } returns false

        // When
        syncJobService.onStartJob(jobParameters)

        // Then
        verify { syncJobService.jobFinished(jobParameters, false) }
    }

    test("onStartJob calls jobFinished with false when needsJobReschedule is false") {
        // Given
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { mocks.mockBackgroundManager.needsJobReschedule } returns false

        // When
        syncJobService.onStartJob(jobParameters)
        awaitIO()

        // Then
        verify { syncJobService.jobFinished(jobParameters, false) }
    }

    test("onStartJob calls jobFinished with true when needsJobReschedule is true") {
        // Given
        val mockBackgroundManager = mocks.mockBackgroundManager
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { mockBackgroundManager.needsJobReschedule } returns true

        // When
        syncJobService.onStartJob(jobParameters)
        awaitIO()

        // Then
        verify { syncJobService.jobFinished(jobParameters, true) }
        verify { mockBackgroundManager.needsJobReschedule = false }
    }

    test("onStartJob resets needsJobReschedule to false after reading it") {
        // Given
        val mockBackgroundManager = mocks.mockBackgroundManager
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { mockBackgroundManager.needsJobReschedule } returns true

        // When
        syncJobService.onStartJob(jobParameters)
        awaitIO()

        // Then
        verify { mockBackgroundManager.needsJobReschedule = false }
    }

    test("onStopJob returns false when OneSignal.getService throws") {
        // Given
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        coEvery { OneSignal.getService<Any>() } throws NullPointerException()

        // When
        val result = syncJobService.onStopJob(jobParameters)

        // Then
        result shouldBe false
    }

    test("onStopJob calls cancelRunBackgroundServices and returns its result") {
        // Given
        val mockBackgroundManager = mocks.mockBackgroundManager
        val syncJobService = mocks.syncJobService
        val jobParameters = mocks.jobParameters
        every { mockBackgroundManager.cancelRunBackgroundServices() } returns true

        // When
        val result = syncJobService.onStopJob(jobParameters)

        // Then
        result shouldBe true
        verify { mockBackgroundManager.cancelRunBackgroundServices() }
    }

    test("onStopJob returns false when cancelRunBackgroundServices returns false") {
        // Given
        val mockBackgroundManager = mocks.mockBackgroundManager
        every { mockBackgroundManager.cancelRunBackgroundServices() } returns false

        // When
        val result = mocks.syncJobService.onStopJob(mocks.jobParameters)

        // Then
        result shouldBe false
        verify { mockBackgroundManager.cancelRunBackgroundServices() }
    }
})
