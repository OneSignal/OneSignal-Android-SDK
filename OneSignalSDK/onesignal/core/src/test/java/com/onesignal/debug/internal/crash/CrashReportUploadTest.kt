package com.onesignal.debug.internal.crash

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.OtelFactory
import com.onesignal.otel.OtelLoggingHelper
import com.onesignal.user.internal.backend.IdentityConstants
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import java.util.UUID
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace
import com.onesignal.user.internal.identity.IDENTITY_NAME_SPACE as identityNameSpace

/**
 * Integration test that uploads a sample crash report to the OneSignal API.
 *
 * This test sends a real HTTP request to the API endpoint configured in OtelConfigRemoteOneSignal.
 *
 * To use this test:
 * 1. Set a valid app ID in the test (replace "YOUR_APP_ID_HERE")
 * 2. Ensure the API endpoint is accessible (check OtelConfigRemoteOneSignal.BASE_URL)
 * 3. Run the test and verify the crash report appears in your backend
 *
 * Note: This test requires network access and will make a real HTTP request.
 *
 * Android Studio Note: If tests fail in Android Studio but work on command line:
 * - File → Invalidate Caches → Invalidate and Restart
 * - File → Sync Project with Gradle Files
 * - Ensure you're running as "Unit Test" (not "Instrumented Test")
 * - Try running from command line: ./gradlew :onesignal:core:testDebugUnitTest --tests "CrashReportUploadTest"
 */
@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class CrashReportUploadTest : FunSpec({
    var appContext: Context? = null
    var sharedPreferences: SharedPreferences? = null

    // TODO: Replace with your actual app ID for testing
    val testAppId = "YOUR_APP_ID_HERE"

    beforeAny {
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
            sharedPreferences = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        }
    }

    beforeSpec {
        // Enable debug logging to see what's being sent
        Logging.logLevel = LogLevel.DEBUG
        Logging.info("🔍 Debug logging enabled for CrashReportUploadTest")
        println("🔍 Debug logging enabled")
    }

    beforeEach {
        // Ensure sharedPreferences is initialized
        if (sharedPreferences == null) {
            appContext = ApplicationProvider.getApplicationContext()
            sharedPreferences = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        }
        // Clear and set up SharedPreferences with test data
        sharedPreferences!!.edit().clear().commit()

        // Set up ConfigModelStore data
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, testAppId)
            put(ConfigModel::pushSubscriptionId.name, "test-subscription-id-${UUID.randomUUID()}")
            val remoteLoggingParams = JSONObject().apply {
                put("logLevel", "ERROR")
            }
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }

        // Set up IdentityModelStore data
        val identityModel = JSONObject().apply {
            put(IdentityConstants.ONESIGNAL_ID, "test-onesignal-id-${UUID.randomUUID()}")
        }
        val identityArray = JSONArray().apply {
            put(identityModel)
        }

        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
            .putString(PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID, UUID.randomUUID().toString())
            .commit()
    }

    afterEach {
        sharedPreferences!!.edit().clear().commit()
    }

    test("should upload sample crash report to API") {
        // Skip if app ID is not configured
        if (testAppId == "YOUR_APP_ID_HERE") {
            println("\n⚠️  Skipping test: Please set testAppId to a valid app ID")
            println("   To run this test, edit the test file and set testAppId to your OneSignal App ID")
            return@test
        }

        runBlocking {
            // Create platform provider with test data from SharedPreferences
            val platformProvider = createAndroidOtelPlatformProvider(appContext!!)

            // Verify app ID is set correctly
            platformProvider.appId shouldBe testAppId
            platformProvider.appIdForHeaders shouldBe testAppId

            // Log platform provider details
            val platformDetails = """
                |📋 Platform Provider Details:
                |   App ID: ${platformProvider.appId}
                |   App ID for Headers: ${platformProvider.appIdForHeaders}
                |   SDK Base: ${platformProvider.sdkBase}
                |   SDK Version: ${platformProvider.sdkBaseVersion}
                |   App Package: ${platformProvider.appPackageId}
                |   App Version: ${platformProvider.appVersion}
                |   Device: ${platformProvider.deviceManufacturer} ${platformProvider.deviceModel}
                |   OS: ${platformProvider.osName} ${platformProvider.osVersion}
                |   OneSignal ID: ${platformProvider.onesignalId}
                |   Push Subscription ID: ${platformProvider.pushSubscriptionId}
                |   App State: ${platformProvider.appState}
                |   Remote Log Level: ${platformProvider.remoteLogLevel}
                |   Install ID: ${runBlocking { platformProvider.getInstallId() }}
            """.trimMargin()
            println(platformDetails)
            Logging.info(platformDetails)

            // Create remote telemetry instance
            println("\n🔧 Creating remote telemetry instance...")
            val remoteTelemetry: IOtelOpenTelemetryRemote = OtelFactory.createRemoteTelemetry(platformProvider)
            remoteTelemetry.shouldBeInstanceOf<IOtelOpenTelemetryRemote>()
            println("   ✅ Remote telemetry created")

            // Create a sample crash report
            val sampleException = RuntimeException("Test crash report from integration test")
            sampleException.stackTrace = arrayOf(
                StackTraceElement("TestClass", "testMethod", "TestFile.kt", 42),
                StackTraceElement("TestClass", "anotherMethod", "TestFile.kt", 30),
                StackTraceElement("Main", "main", "Main.kt", 10)
            )

            val crashReportInfo = """
                |📤 Uploading crash report to API...
                |   App ID: ${platformProvider.appId}
                |   Exception Type: ${sampleException.javaClass.name}
                |   Exception Message: ${sampleException.message}
                |   Stack Trace Length: ${sampleException.stackTraceToString().length} chars
                |
                |📦 Crash Report Payload:
                |   Level: FATAL
                |   Message: Sample crash report for API testing
                |   Exception Type: ${sampleException.javaClass.name}
                |   Exception Message: ${sampleException.message}
                |   Stack Trace Preview: ${sampleException.stackTraceToString().take(200)}...
            """.trimMargin()
            println(crashReportInfo)
            Logging.info(crashReportInfo)

            // Use OtelLoggingHelper to send the crash report (this handles all OpenTelemetry internals)
            println("\n🚀 Calling OtelLoggingHelper.logToOtel()...")
            Logging.info("🚀 Calling OtelLoggingHelper.logToOtel()...")
            try {
                OtelLoggingHelper.logToOtel(
                    telemetry = remoteTelemetry,
                    level = "FATAL",
                    message = "Sample crash report for API testing",
                    exceptionType = sampleException.javaClass.name,
                    exceptionMessage = sampleException.message,
                    exceptionStacktrace = sampleException.stackTraceToString()
                )
                val successMsg = "   ✅ logToOtel() completed successfully"
                println(successMsg)
                Logging.info(successMsg)
            } catch (e: Exception) {
                val errorMsg = "   ❌ Error calling logToOtel(): ${e.message}"
                println(errorMsg)
                Logging.error(errorMsg, e)
                e.printStackTrace()
                throw e
            }

            // Note: forceFlush() returns CompletableResultCode which is not accessible from core module
            // OpenTelemetry will automatically batch and send the logs, so we just wait a bit
            println("\n🔄 Waiting for telemetry to be sent (OpenTelemetry batches automatically)...")
            println("   Batch delay: 1 second (configured in OtelConfigShared)")
            println("   Waiting 5 seconds to ensure batch is sent...")
            for (i in 1..5) {
                delay(1000)
                println("   ⏳ Waited $i second(s)...")
            }

            // Note: CompletableResultCode is not directly accessible from core module
            // We just wait and assume success if no exception was thrown
            println("\n✅ Crash report upload process completed!")
            println("   Check your backend dashboard to verify the crash report was received")
            println("   Note: OpenTelemetry batches requests, so it may take a moment to appear")
            println("   Expected endpoint: https://api.staging.onesignal.com/sdk/otel/v1/logs?app_id=${platformProvider.appId}")
        }
    }

    test("should upload crash report using OtelLoggingHelper") {
        // Skip if app ID is not configured
        if (testAppId == "YOUR_APP_ID_HERE") {
            println("⚠️  Skipping test: Please set testAppId to a valid app ID")
            return@test
        }

        runBlocking {
            // Create platform provider
            val platformProvider = createAndroidOtelPlatformProvider(appContext!!)

            // Create remote telemetry
            val remoteTelemetry: IOtelOpenTelemetryRemote = OtelFactory.createRemoteTelemetry(platformProvider)

            // Create sample exception
            val sampleException = IllegalStateException("Test exception from OtelLoggingHelper test")

            println("📤 Uploading crash report via OtelLoggingHelper...")
            println("   App ID: ${platformProvider.appId}")

            // Use OtelLoggingHelper to send the crash report
            OtelLoggingHelper.logToOtel(
                telemetry = remoteTelemetry,
                level = "FATAL",
                message = "Sample crash report via OtelLoggingHelper",
                exceptionType = sampleException.javaClass.name,
                exceptionMessage = sampleException.message,
                exceptionStacktrace = sampleException.stackTraceToString()
            )

            // Note: forceFlush() returns CompletableResultCode which is not accessible from core module
            // OpenTelemetry will automatically batch and send the logs, so we just wait a bit
            println("🔄 Waiting for telemetry to be sent (OpenTelemetry batches automatically)...")
            delay(3000) // Wait 3 seconds for automatic batching to send

            println("✅ Crash report sent via OtelLoggingHelper!")
            println("   Check your backend dashboard to verify the crash report was received")
            println("   Note: OpenTelemetry batches requests, so it may take a moment to appear")
        }
    }

    test("should verify platform provider has all required fields for crash report") {
        println("\n🔍 Testing Platform Provider Configuration...")

        val platformProvider = createAndroidOtelPlatformProvider(appContext!!)

        println("\n📋 Platform Provider Fields:")
        println("   App ID: ${platformProvider.appId}")
        println("   App ID for Headers: ${platformProvider.appIdForHeaders}")
        println("   SDK Base: ${platformProvider.sdkBase}")
        println("   SDK Version: ${platformProvider.sdkBaseVersion}")
        println("   App Package: ${platformProvider.appPackageId}")
        println("   App Version: ${platformProvider.appVersion}")
        println("   Device: ${platformProvider.deviceManufacturer} ${platformProvider.deviceModel}")
        println("   OS: ${platformProvider.osName} ${platformProvider.osVersion} (Build: ${platformProvider.osBuildId})")
        println("   OneSignal ID: ${platformProvider.onesignalId}")
        println("   Push Subscription ID: ${platformProvider.pushSubscriptionId}")
        println("   App State: ${platformProvider.appState}")
        println("   Process Uptime: ${platformProvider.processUptime}ms")
        println("   Thread Name: ${platformProvider.currentThreadName}")
        println("   Remote Log Level: ${platformProvider.remoteLogLevel}")

        runBlocking {
            val installId = platformProvider.getInstallId()
            println("   Install ID: $installId")
            installId shouldNotBe null
            installId.isNotEmpty() shouldBe true
        }

        // Verify all required fields are present
        platformProvider.appId shouldNotBe null
        platformProvider.appIdForHeaders shouldNotBe null
        platformProvider.sdkBase shouldBe "android"
        platformProvider.sdkBaseVersion shouldNotBe null
        platformProvider.appPackageId shouldBe appContext!!.packageName // Use actual package name from context
        platformProvider.appVersion shouldNotBe null
        platformProvider.deviceManufacturer shouldNotBe null
        platformProvider.deviceModel shouldNotBe null
        platformProvider.osName shouldBe "Android"
        platformProvider.osVersion shouldNotBe null
        platformProvider.osBuildId shouldNotBe null

        println("\n✅ All platform provider fields verified!")

        // Show what would be sent in a crash report
        println("\n📦 Sample Crash Report Attributes (what would be sent):")
        println("   Top-Level (Resource):")
        println("     - service.name: OneSignalDeviceSDK")
        println("     - ossdk.install_id: ${runBlocking { platformProvider.getInstallId() }}")
        println("     - ossdk.sdk_base: ${platformProvider.sdkBase}")
        println("     - ossdk.sdk_base_version: ${platformProvider.sdkBaseVersion}")
        println("     - ossdk.app_package_id: ${platformProvider.appPackageId}")
        println("     - ossdk.app_version: ${platformProvider.appVersion}")
        println("     - device.manufacturer: ${platformProvider.deviceManufacturer}")
        println("     - device.model.identifier: ${platformProvider.deviceModel}")
        println("     - os.name: ${platformProvider.osName}")
        println("     - os.version: ${platformProvider.osVersion}")
        println("     - os.build_id: ${platformProvider.osBuildId}")
        println("   Per-Event:")
        println("     - log.record.uid: <UUID>")
        println("     - ossdk.app_id: ${platformProvider.appId}")
        println("     - ossdk.onesignal_id: ${platformProvider.onesignalId}")
        println("     - ossdk.push_subscription_id: ${platformProvider.pushSubscriptionId}")
        println("     - app.state: ${platformProvider.appState}")
        println("     - process.uptime: ${platformProvider.processUptime}")
        println("     - thread.name: ${platformProvider.currentThreadName}")
        println("   Log-Specific:")
        println("     - log.message: <crash message>")
        println("     - log.level: FATAL")
        println("     - exception.type: <exception class>")
        println("     - exception.message: <exception message>")
        println("     - exception.stacktrace: <full stack trace>")
        println("\n   Expected Endpoint: https://api.staging.onesignal.com/sdk/otel/v1/logs?app_id=${platformProvider.appId}")
    }
})
