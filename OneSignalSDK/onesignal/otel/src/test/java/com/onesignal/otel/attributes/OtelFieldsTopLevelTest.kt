package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

class OtelFieldsTopLevelTest : FunSpec({
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val fields = OtelFieldsTopLevel(mockPlatformProvider)

    test("getAttributes should include all top-level fields") {
        coEvery { mockPlatformProvider.getInstallId() } returns "test-install-id"
        every { mockPlatformProvider.sdkBase } returns "android"
        every { mockPlatformProvider.sdkBaseVersion } returns "1.0.0"
        every { mockPlatformProvider.appPackageId } returns "com.test.app"
        every { mockPlatformProvider.appVersion } returns "1.0"
        every { mockPlatformProvider.deviceManufacturer } returns "TestManufacturer"
        every { mockPlatformProvider.deviceModel } returns "TestModel"
        every { mockPlatformProvider.osName } returns "Android"
        every { mockPlatformProvider.osVersion } returns "10"
        every { mockPlatformProvider.osBuildId } returns "TEST123"
        every { mockPlatformProvider.sdkWrapper } returns "unity"
        every { mockPlatformProvider.sdkWrapperVersion } returns "2.0.0"

        runBlocking {
            val attributes = fields.getAttributes()

            attributes["ossdk.install_id"] shouldBe "test-install-id"
            attributes["ossdk.sdk_base"] shouldBe "android"
            attributes["ossdk.sdk_base_version"] shouldBe "1.0.0"
            attributes["ossdk.app_package_id"] shouldBe "com.test.app"
            attributes["ossdk.app_version"] shouldBe "1.0"
            attributes["device.manufacturer"] shouldBe "TestManufacturer"
            attributes["device.model.identifier"] shouldBe "TestModel"
            attributes["os.name"] shouldBe "Android"
            attributes["os.version"] shouldBe "10"
            attributes["os.build_id"] shouldBe "TEST123"
            attributes["ossdk.sdk_wrapper"] shouldBe "unity"
            attributes["ossdk.sdk_wrapper_version"] shouldBe "2.0.0"
        }
    }

    test("getAttributes should exclude null wrapper fields") {
        coEvery { mockPlatformProvider.getInstallId() } returns "test-install-id"
        every { mockPlatformProvider.sdkBase } returns "android"
        every { mockPlatformProvider.sdkBaseVersion } returns "1.0.0"
        every { mockPlatformProvider.appPackageId } returns "com.test.app"
        every { mockPlatformProvider.appVersion } returns "1.0"
        every { mockPlatformProvider.deviceManufacturer } returns "Test"
        every { mockPlatformProvider.deviceModel } returns "TestDevice"
        every { mockPlatformProvider.osName } returns "Android"
        every { mockPlatformProvider.osVersion } returns "10"
        every { mockPlatformProvider.osBuildId } returns "TEST123"
        every { mockPlatformProvider.sdkWrapper } returns null
        every { mockPlatformProvider.sdkWrapperVersion } returns null

        runBlocking {
            val attributes = fields.getAttributes()

            attributes.keys shouldNotContain "ossdk.sdk_wrapper"
            attributes.keys shouldNotContain "ossdk.sdk_wrapper_version"
        }
    }
})
