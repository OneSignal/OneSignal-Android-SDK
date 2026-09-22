package com.onesignal.core.internal.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.device.impl.DeviceService
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk

@RobolectricTest
class DeviceServiceTests : FunSpec({
    test("devicetype is Huawei when preferHMS manifest value is true when a device supports HMS and FCM") {
        // Given
        val mockDeviceService = Mocks().deviceService
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
        val mockDeviceService = Mocks().deviceService
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
        val mockDeviceService = Mocks().deviceService
        every { mockDeviceService.supportsHMS } returns true
        every { mockDeviceService.supportsGooglePush() } returns true

        // When
        val deviceType = mockDeviceService.deviceType

        // Then
        deviceType shouldBe IDeviceService.DeviceType.Android
    }

    test("isGMSInstalledAndEnabled is false when PackageManager returns a null PackageInfo") {
        val deviceService = deviceServiceWithPackageInfo(null)

        deviceService.isGMSInstalledAndEnabled shouldBe false
    }

    test("isGMSInstalledAndEnabled is false when PackageInfo.applicationInfo is null") {
        val deviceService = deviceServiceWithPackageInfo(PackageInfo())

        deviceService.isGMSInstalledAndEnabled shouldBe false
    }

    test("isGMSInstalledAndEnabled is true when the package is enabled") {
        val info = PackageInfo()
        info.applicationInfo = ApplicationInfo().apply { enabled = true }
        val deviceService = deviceServiceWithPackageInfo(info)

        deviceService.isGMSInstalledAndEnabled shouldBe true
    }
})

private fun deviceServiceWithPackageInfo(info: PackageInfo?): DeviceService {
    val pm = mockk<PackageManager>()
    every { pm.getPackageInfo(any<String>(), any<Int>()) } returns info
    val appContext = mockk<Context>()
    every { appContext.packageManager } returns pm
    val applicationService = MockHelper.applicationService()
    every { applicationService.appContext } returns appContext
    return DeviceService(applicationService)
}

private class Mocks {
    val applicationService =
        run {
            val mockApplicationService = MockHelper.applicationService()
            every { mockApplicationService.appContext } returns ApplicationProvider.getApplicationContext()
            mockApplicationService
        }

    val deviceService: DeviceService by lazy {
        spyk(
            DeviceService(
                applicationService,
            ),
        )
    }
}
