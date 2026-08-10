package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking

class OtelFieldsTopLevelTest : FunSpec({
    val mockPlatformProvider = mockk<IOtelPlatformProvider>(relaxed = true)
    val fields = OtelFieldsTopLevel(mockPlatformProvider)

    fun setupDefaultMocks(
        installId: String = "test-install-id",
        sdkWrapper: String? = null,
        sdkWrapperVersion: String? = null,
        kotlinVersion: String? = null,
        swiftVersion: String? = null,
        additionalVersionAttributes: Map<String, String> = emptyMap(),
    ) {
        coEvery { mockPlatformProvider.getInstallId() } returns installId
        every { mockPlatformProvider.sdkBase } returns "android"
        every { mockPlatformProvider.sdkBaseVersion } returns "1.0.0"
        every { mockPlatformProvider.appPackageId } returns "com.test.app"
        every { mockPlatformProvider.appVersion } returns "1.0"
        every { mockPlatformProvider.deviceManufacturer } returns "TestManufacturer"
        every { mockPlatformProvider.deviceModel } returns "TestModel"
        every { mockPlatformProvider.osName } returns "Android"
        every { mockPlatformProvider.osVersion } returns "10"
        every { mockPlatformProvider.osBuildId } returns "TEST123"
        every { mockPlatformProvider.sdkWrapper } returns sdkWrapper
        every { mockPlatformProvider.sdkWrapperVersion } returns sdkWrapperVersion
        every { mockPlatformProvider.kotlinVersion } returns kotlinVersion
        every { mockPlatformProvider.swiftVersion } returns swiftVersion
        every { mockPlatformProvider.additionalVersionAttributes } returns additionalVersionAttributes
    }

    beforeEach { clearMocks(mockPlatformProvider) }

    test("getAttributes should include all required top-level fields") {
        setupDefaultMocks()

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
            attributes.keys shouldNotContain "ossdk.kotlin_version"
            attributes.keys shouldNotContain "ossdk.swift_version"
        }
    }

    test("getAttributes should include wrapper fields when present") {
        setupDefaultMocks(sdkWrapper = "unity", sdkWrapperVersion = "2.0.0")

        runBlocking {
            val attributes = fields.getAttributes()

            attributes["ossdk.sdk_wrapper"] shouldBe "unity"
            attributes["ossdk.sdk_wrapper_version"] shouldBe "2.0.0"
        }
    }

    test("getAttributes should exclude null wrapper fields") {
        setupDefaultMocks(sdkWrapper = null, sdkWrapperVersion = null)

        runBlocking {
            val attributes = fields.getAttributes()

            attributes.keys shouldNotContain "ossdk.sdk_wrapper"
            attributes.keys shouldNotContain "ossdk.sdk_wrapper_version"
        }
    }

    test("getAttributes should include kotlin and swift versions when provided") {
        setupDefaultMocks(kotlinVersion = "1.9.25", swiftVersion = "5.10")

        runBlocking {
            val attributes = fields.getAttributes()

            attributes["ossdk.kotlin_version"] shouldBe "1.9.25"
            attributes["ossdk.swift_version"] shouldBe "5.10"
        }
    }

    test("getAttributes should omit blank language versions") {
        setupDefaultMocks(kotlinVersion = "   ", swiftVersion = "")

        runBlocking {
            val attributes = fields.getAttributes()

            attributes.keys shouldNotContain "ossdk.kotlin_version"
            attributes.keys shouldNotContain "ossdk.swift_version"
        }
    }

    test("getAttributes should merge additionalVersionAttributes under ossdk prefix") {
        setupDefaultMocks(
            kotlinVersion = "1.9.25",
            additionalVersionAttributes =
                mapOf(
                    "java_version" to "17",
                    "kotlin_version" to "should-not-win",
                    "install_id" to "forged-install",
                    "ossdk.ndk_version" to "26.1",
                    "agp_version" to "  ",
                ),
        )

        runBlocking {
            val attributes = fields.getAttributes()

            attributes["ossdk.java_version"] shouldBe "17"
            attributes["ossdk.ndk_version"] shouldBe "26.1"
            attributes["ossdk.kotlin_version"] shouldBe "1.9.25"
            attributes["ossdk.install_id"] shouldBe "test-install-id"
            attributes.keys shouldNotContain "ossdk.ossdk.ndk_version"
            attributes.keys shouldNotContain "ossdk.agp_version"
        }
    }

    test("getAttributes should never include ossdk.feature_flags (now per-event)") {
        setupDefaultMocks()

        runBlocking {
            val attributes = fields.getAttributes()

            attributes.keys shouldNotContain "ossdk.feature_flags"
        }
    }
})
