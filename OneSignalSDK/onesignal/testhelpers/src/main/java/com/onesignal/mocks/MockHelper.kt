package com.onesignal.mocks

import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.time.ITime
import com.onesignal.session.internal.session.SessionModel
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import java.util.UUID

/**
 * Singleton which provides common mock services.
 */
object MockHelper {
    internal fun time(time: Long): ITime {
        val mockTime = mockk<ITime>()
        every { mockTime.currentTimeMillis } returns time

        return mockTime
    }

    internal fun applicationService(): IApplicationService {
        val mockAppService = mockk<IApplicationService>()

        every { mockAppService.addApplicationLifecycleHandler(any()) } just Runs
        every { mockAppService.removeApplicationLifecycleHandler(any()) } just Runs

        return mockAppService
    }

    const val DEFAULT_APP_ID = "appId"

    internal fun configModelStore(action: ((ConfigModel) -> Unit)? = null): ConfigModelStore {
        val configModel = ConfigModel()

        configModel.opRepoExecutionInterval = 1
        configModel.opRepoPostWakeDelay = 1
        configModel.opRepoPostCreateDelay = 1
        configModel.opRepoPostCreateRetryUpTo = 1
        configModel.opRepoDefaultFailRetryBackoff = 1
        configModel.foregroundFetchNotificationPermissionInterval = 1

        configModel.appId = DEFAULT_APP_ID

        if (action != null) {
            action(configModel)
        }

        val mockConfigStore = mockk<ConfigModelStore>()

        every { mockConfigStore.model } returns configModel
        every { mockConfigStore.subscribe(any()) } just runs

        return mockConfigStore
    }

    internal fun identityModelStore(action: ((IdentityModel) -> Unit)? = null): IdentityModelStore {
        val identityModel = IdentityModel()

        identityModel.id = "-singleton"
        identityModel.onesignalId = UUID.randomUUID().toString()

        if (action != null) {
            action(identityModel)
        }

        val mockIdentityStore = mockk<IdentityModelStore>(relaxed = true)

        every { mockIdentityStore.model } returns identityModel

        return mockIdentityStore
    }

    internal fun propertiesModelStore(action: ((PropertiesModel) -> Unit)? = null): PropertiesModelStore {
        val propertiesModel = PropertiesModel()

        propertiesModel.id = "-singleton"
        propertiesModel.onesignalId = UUID.randomUUID().toString()

        if (action != null) {
            action(propertiesModel)
        }

        val mockPropertiesStore = mockk<PropertiesModelStore>()

        every { mockPropertiesStore.model } returns propertiesModel

        return mockPropertiesStore
    }

    internal fun sessionModelStore(action: ((SessionModel) -> Unit)? = null): SessionModelStore {
        val sessionModel = SessionModel()

        if (action != null) {
            action(sessionModel)
        }

        val mockSessionStore = mockk<SessionModelStore>()

        every { mockSessionStore.model } returns sessionModel

        return mockSessionStore
    }

    internal fun languageContext(language: String = "en"): ILanguageContext {
        val mockLanguageContext = mockk<ILanguageContext>()

        every { mockLanguageContext.language } returns language

        return mockLanguageContext
    }

    internal fun deviceService(): IDeviceService {
        val deviceService = mockk<IDeviceService>()
        every { deviceService.deviceType } returns IDeviceService.DeviceType.Android
        return deviceService
    }
}
