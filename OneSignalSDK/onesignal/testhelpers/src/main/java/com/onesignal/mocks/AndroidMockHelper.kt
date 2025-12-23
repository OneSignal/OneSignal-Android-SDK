package com.onesignal.mocks

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onesignal.core.internal.application.IApplicationService
import io.mockk.every
import io.mockk.mockk

/**
 * Singleton which provides common mock services when running in an Android environment.
 */
object AndroidMockHelper {
    internal fun applicationService(): IApplicationService {
        val mockAppService = MockHelper.applicationService()

        try {
            // Robolectric
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
        } catch (_: IllegalStateException) {
            // Fallback to simpler mock (using mockk) if Robolectric is not used in the test
            every { mockAppService.appContext } returns mockk<Context>(relaxed = true)
        }

        return mockAppService
    }
}
