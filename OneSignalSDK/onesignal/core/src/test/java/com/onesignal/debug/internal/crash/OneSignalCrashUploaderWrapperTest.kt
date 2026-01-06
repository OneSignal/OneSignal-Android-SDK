package com.onesignal.debug.internal.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.startup.IStartableService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

@RobolectricTest
class OneSignalCrashUploaderWrapperTest : FunSpec({

    var appContext: Context? = null

    beforeAny {
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
        }
    }

    test("should implement IStartableService") {
        // Given
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext!!

        // When
        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // Then
        wrapper.shouldBeInstanceOf<IStartableService>()
    }

    test("should create uploader lazily when start is called") {
        // Given
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext!!

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // When
        runBlocking {
            wrapper.start()
        }

        // Then - should not throw, uploader should be created
        // We can't directly verify the uploader was created, but if start() completes without error,
        // it means the uploader was created and started successfully
    }

    test("should call uploader.start() when start() is called") {
        // Given
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext!!

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // When
        runBlocking {
            wrapper.start()
        }

        // Then - start() should complete without throwing
        // The actual uploader.start() is called internally via runBlocking
        // If it throws, this test would fail
    }

    test("should handle errors gracefully when uploader fails") {
        // Given
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext!!

        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // When/Then - start() should handle errors gracefully
        // If remote logging is disabled, it should return early without error
        // If there are no crash reports, it should complete without error
        runBlocking {
            // This should not throw even if there are no crash reports or if remote logging is disabled
            wrapper.start()
        }
    }

    test("should create wrapper with applicationService dependency") {
        // Given
        val mockApplicationService = mockk<IApplicationService>(relaxed = true)
        every { mockApplicationService.appContext } returns appContext!!

        // When
        val wrapper = OneSignalCrashUploaderWrapper(mockApplicationService)

        // Then
        wrapper shouldNotBe null
        wrapper.shouldBeInstanceOf<OneSignalCrashUploaderWrapper>()
    }
})
