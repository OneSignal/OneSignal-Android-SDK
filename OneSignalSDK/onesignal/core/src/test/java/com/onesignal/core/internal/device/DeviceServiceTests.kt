package com.onesignal.core.internal.device

import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.device.impl.DeviceService
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk

@RobolectricTest
class DeviceServiceTests : FunSpec({
    test("devicetype is Huawei when preferHMS manifest value is true when a device supports HMS and FCM") {
        // Given
        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.appContext } returns ApplicationProvider.getApplicationContext()

        val mockDeviceService =
            spyk(
                DeviceService(
                    mockApplicationService,
                ),
            )
        every { mockDeviceService.supportsHMS } returns true
        every { mockDeviceService.supportsGooglePush() } returns true
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBoolean(ApplicationProvider.getApplicationContext(), "com.onesignal.preferHMS") } returns true

        // When
        val deviceType = mockDeviceService.deviceType

        // Then
        deviceType shouldBe IDeviceService.DeviceType.Huawei
    }

    test("devicetype is FCM when preferHMS manifest value is false when a device supports HMS and FCM") {
        // Given
        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.appContext } returns ApplicationProvider.getApplicationContext()

        val mockDeviceService =
            spyk(
                DeviceService(
                    mockApplicationService,
                ),
            )
        every { mockDeviceService.supportsHMS } returns true
        every { mockDeviceService.supportsGooglePush() } returns true
        mockkObject(AndroidUtils)
        every { AndroidUtils.getManifestMetaBoolean(ApplicationProvider.getApplicationContext(), "com.onesignal.preferHMS") } returns false

        // When
        val deviceType = mockDeviceService.deviceType

        // Then
        deviceType shouldBe IDeviceService.DeviceType.Android
    }

    test("devicetype is FCM when preferHMS manifest value is missing when a device supports HMS and FCM") {
        // Given
        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.appContext } returns ApplicationProvider.getApplicationContext()

        val mockDeviceService =
            spyk(
                DeviceService(
                    mockApplicationService,
                ),
            )
        every { mockDeviceService.supportsHMS } returns true
        every { mockDeviceService.supportsGooglePush() } returns true
        // When
        val deviceType = mockDeviceService.deviceType

        // Then
        deviceType shouldBe IDeviceService.DeviceType.Android
    }
})
