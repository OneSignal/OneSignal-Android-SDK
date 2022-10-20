package com.onesignal.core.tests.mocks

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.SessionModel
import com.onesignal.core.internal.models.SessionModelStore
import com.onesignal.core.internal.time.ITime
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

/**
 * Singleton which provides common mock services.
 */
internal object MockHelper {
    fun time(time: Long): ITime {
        val mockTime = mockk<ITime>()
        every { mockTime.currentTimeMillis } returns time

        return mockTime
    }

    fun applicationService(): IApplicationService {
        val mockAppService = mockk<IApplicationService>()

        every { mockAppService.addApplicationLifecycleHandler(any()) } just Runs

        return mockAppService
    }

    const val DEFAULT_APP_ID = "appId"
    fun configModelStore(action: ((ConfigModel) -> Unit)? = null): ConfigModelStore {
        val configModel = ConfigModel()

        configModel.appId = DEFAULT_APP_ID

        if (action != null) {
            action(configModel)
        }

        val mockConfigStore = mockk<ConfigModelStore>()

        every { mockConfigStore.get() } returns configModel

        return mockConfigStore
    }

    fun sessionModelStore(action: ((SessionModel) -> Unit)? = null): SessionModelStore {
        val sessionModel = SessionModel()

        if (action != null) {
            action(sessionModel)
        }

        val mockSessionStore = mockk<SessionModelStore>()

        every { mockSessionStore.get() } returns sessionModel

        return mockSessionStore
    }

    const val DEFAULT_DEVICE_TYPE = 1

    fun deviceService(): IDeviceService {
        val deviceService = mockk<IDeviceService>()
        every { deviceService.deviceType } returns DEFAULT_DEVICE_TYPE
        return deviceService
    }
}