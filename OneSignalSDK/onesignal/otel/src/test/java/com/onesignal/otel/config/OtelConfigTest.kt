package com.onesignal.otel.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.opentelemetry.semconv.ServiceAttributes

class OtelConfigTest : FunSpec({

    // ===== OtelConfigShared.ResourceConfig Tests =====

    test("ResourceConfig should create resource with service name") {
        val resource = OtelConfigShared.ResourceConfig.create(emptyMap())

        resource.attributes.get(ServiceAttributes.SERVICE_NAME) shouldBe "OneSignalDeviceSDK"
    }

    test("ResourceConfig should include custom attributes") {
        val customAttributes = mapOf(
            "custom.key1" to "value1",
            "custom.key2" to "value2"
        )

        val resource = OtelConfigShared.ResourceConfig.create(customAttributes)

        resource.attributes.get(ServiceAttributes.SERVICE_NAME) shouldBe "OneSignalDeviceSDK"
        resource.attributes.asMap().entries.any { it.key.key == "custom.key1" } shouldBe true
        resource.attributes.asMap().entries.any { it.key.key == "custom.key2" } shouldBe true
    }

    test("ResourceConfig should handle empty attributes map") {
        val resource = OtelConfigShared.ResourceConfig.create(emptyMap())

        resource shouldNotBe null
        resource.attributes.get(ServiceAttributes.SERVICE_NAME) shouldBe "OneSignalDeviceSDK"
    }

    // ===== OtelConfigShared.LogLimitsConfig Tests =====

    test("LogLimitsConfig should create valid log limits") {
        val logLimits = OtelConfigShared.LogLimitsConfig.logLimits()

        logLimits shouldNotBe null
        logLimits.maxNumberOfAttributes shouldBe 128
        logLimits.maxAttributeValueLength shouldBe 32000
    }

    // ===== OtelConfigShared.LogRecordProcessorConfig Tests =====

    test("LogRecordProcessorConfig should create batch processor") {
        val mockExporter = io.mockk.mockk<io.opentelemetry.sdk.logs.export.LogRecordExporter>(relaxed = true)

        val processor = OtelConfigShared.LogRecordProcessorConfig.batchLogRecordProcessor(mockExporter)

        processor shouldNotBe null
    }

    // ===== OtelConfigRemoteOneSignal Tests =====

    test("BASE_URL should point to production endpoint") {
        OtelConfigRemoteOneSignal.SdkLoggerProviderConfig.BASE_URL shouldBe "https://api.onesignal.com/sdk/otel"
    }

    test("HttpRecordBatchExporter should create exporter with correct endpoint") {
        val headers = mapOf("X-Test-Header" to "test-value")
        val appId = "test-app-id"

        val exporter = OtelConfigRemoteOneSignal.HttpRecordBatchExporter.create(headers, appId)

        exporter shouldNotBe null
    }

    test("LogRecordExporterConfig should create OTLP HTTP exporter") {
        val headers = mapOf("Authorization" to "Bearer token")
        val endpoint = "https://example.com/v1/logs"

        val exporter = OtelConfigRemoteOneSignal.LogRecordExporterConfig.otlpHttpLogRecordExporter(
            headers,
            endpoint
        )

        exporter shouldNotBe null
    }

    test("SdkLoggerProviderConfig should create logger provider") {
        val resource = OtelConfigShared.ResourceConfig.create(emptyMap())
        val headers = mapOf("X-OneSignal-App-Id" to "test-app-id")

        val provider = OtelConfigRemoteOneSignal.SdkLoggerProviderConfig.create(
            resource,
            headers,
            "test-app-id"
        )

        provider shouldNotBe null
    }

    // ===== OtelConfigCrashFile Tests =====

    test("OtelConfigCrashFile should create file log storage") {
        val tempDir = System.getProperty("java.io.tmpdir") + "/otel-test-" + System.currentTimeMillis()
        java.io.File(tempDir).mkdirs()

        try {
            val storage = OtelConfigCrashFile.SdkLoggerProviderConfig.getFileLogRecordStorage(
                tempDir,
                5000L
            )

            storage shouldNotBe null
        } finally {
            java.io.File(tempDir).deleteRecursively()
        }
    }

    test("OtelConfigCrashFile should create logger provider") {
        val resource = OtelConfigShared.ResourceConfig.create(emptyMap())
        val tempDir = System.getProperty("java.io.tmpdir") + "/otel-test-" + System.currentTimeMillis()
        java.io.File(tempDir).mkdirs()

        try {
            val provider = OtelConfigCrashFile.SdkLoggerProviderConfig.create(
                resource,
                tempDir,
                5000L
            )

            provider shouldNotBe null
        } finally {
            java.io.File(tempDir).deleteRecursively()
        }
    }
})
