package com.onesignal.mocks

import androidx.test.core.app.ApplicationProvider
import com.onesignal.core.internal.application.IApplicationService
import io.mockk.every

/**
 * Singleton which provides common mock services when running in an Android environment.
 */
object AndroidMockHelper {
    fun applicationService(): IApplicationService {
        val mockAppService = MockHelper.applicationService()

        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

        return mockAppService
    }
}
