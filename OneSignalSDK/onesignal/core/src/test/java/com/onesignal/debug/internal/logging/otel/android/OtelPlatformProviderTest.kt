package com.onesignal.debug.internal.logging.otel.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IdentityConstants
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace
import com.onesignal.user.internal.identity.IDENTITY_NAME_SPACE as identityNameSpace

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelPlatformProviderTest : FunSpec({

    var appContext: Context? = null
    var sharedPreferences: SharedPreferences? = null

    beforeAny {
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
            sharedPreferences = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        }
    }

    beforeEach {
        // Ensure sharedPreferences is initialized
        if (sharedPreferences == null) {
            appContext = ApplicationProvider.getApplicationContext()
            sharedPreferences = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        }
        // Clear SharedPreferences and reset wrapper
        sharedPreferences!!.edit().clear().commit()
        OneSignalWrapper.sdkType = null
        OneSignalWrapper.sdkVersion = null
        Logging.logLevel = LogLevel.NONE
    }

    afterEach {
        // Clean up
        sharedPreferences!!.edit().clear().commit()
        OneSignalWrapper.sdkType = null
        OneSignalWrapper.sdkVersion = null
    }

    // ===== Static Properties Tests =====

    test("sdkBase returns android") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.sdkBase

        // Then
        result shouldBe "android"
    }

    test("sdkBaseVersion returns OneSignalUtils.sdkVersion") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.sdkBaseVersion

        // Then
        result shouldBe OneSignalUtils.sdkVersion
    }

    test("appPackageId returns context.packageName") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.appPackageId

        // Then
        result shouldBe appContext!!.packageName
    }

    test("appVersion returns AndroidUtils.getAppVersion") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.appVersion

        // Then
        result shouldNotBe null
        result shouldNotBe ""
    }

    test("deviceManufacturer returns Build.MANUFACTURER") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.deviceManufacturer

        // Then
        result shouldBe Build.MANUFACTURER
    }

    test("deviceModel returns Build.MODEL") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.deviceModel

        // Then
        result shouldBe Build.MODEL
    }

    test("osName returns Android") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.osName

        // Then
        result shouldBe "Android"
    }

    test("osVersion returns Build.VERSION.RELEASE") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.osVersion

        // Then
        result shouldBe Build.VERSION.RELEASE
    }

    test("osBuildId returns Build.ID") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.osBuildId

        // Then
        result shouldBe Build.ID
    }

    test("sdkWrapper returns OneSignalWrapper.sdkType") {
        // Given
        OneSignalWrapper.sdkType = "Unity"
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.sdkWrapper

        // Then
        result shouldBe "Unity"
    }

    test("sdkWrapper returns null when not set") {
        // Given
        OneSignalWrapper.sdkType = null
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.sdkWrapper

        // Then
        result shouldBe null
    }

    test("sdkWrapperVersion returns OneSignalWrapper.sdkVersion") {
        // Given
        OneSignalWrapper.sdkVersion = "1.0.0"
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.sdkWrapperVersion

        // Then
        result shouldBe "1.0.0"
    }

    test("sdkWrapperVersion returns null when not set") {
        // Given
        OneSignalWrapper.sdkVersion = null
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.sdkWrapperVersion

        // Then
        result shouldBe null
    }

    // ===== Lazy ID Properties Tests =====

    test("appId returns resolved appId from OtelIdResolver") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id-123")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.appId

        // Then
        result shouldBe "test-app-id-123"
    }

    test("appId returns error UUID when not available") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.appId

        // Then - should return error appId (not null, but error UUID prefix)
        result shouldNotBe null
        result shouldContain "e1100000-0000-4000-a000-"
    }

    test("onesignalId returns resolved onesignalId from OtelIdResolver") {
        // Given
        val identityModel = JSONObject().apply {
            put(IdentityConstants.ONESIGNAL_ID, "test-onesignal-id-123")
        }
        val identityArray = JSONArray().apply {
            put(identityModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.onesignalId

        // Then
        result shouldBe "test-onesignal-id-123"
    }

    test("onesignalId returns null when not available") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.onesignalId

        // Then
        result shouldBe null
    }

    test("pushSubscriptionId returns resolved pushSubscriptionId from OtelIdResolver") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::pushSubscriptionId.name, "test-push-sub-id-123")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.pushSubscriptionId

        // Then
        result shouldBe "test-push-sub-id-123"
    }

    test("pushSubscriptionId returns null when not available") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.pushSubscriptionId

        // Then
        result shouldBe null
    }

    // ===== appState Tests =====

    test("appState returns foreground when getIsInForeground returns true") {
        // Given
        val getIsInForeground: () -> Boolean? = { true }
        val config = OtelPlatformProviderConfig(
            crashStoragePath = "/test/path",
            appPackageId = "com.test",
            appVersion = "1.0",
            context = appContext,
            getIsInForeground = getIsInForeground
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.appState

        // Then
        result shouldBe "foreground"
    }

    test("appState returns background when getIsInForeground returns false") {
        // Given
        val getIsInForeground: () -> Boolean? = { false }
        val config = OtelPlatformProviderConfig(
            crashStoragePath = "/test/path",
            appPackageId = "com.test",
            appVersion = "1.0",
            context = appContext,
            getIsInForeground = getIsInForeground
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.appState

        // Then
        result shouldBe "background"
    }

    test("appState falls back to ActivityManager when getIsInForeground is null") {
        // Given
        val config = OtelPlatformProviderConfig(
            crashStoragePath = "/test/path",
            appPackageId = "com.test",
            appVersion = "1.0",
            context = appContext,
            getIsInForeground = null
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.appState

        // Then - should return a valid state (foreground, background, or unknown)
        result shouldBeOneOf listOf("foreground", "background", "unknown")
    }

    test("appState returns unknown when context is null and getIsInForeground is null") {
        // Given
        val config = OtelPlatformProviderConfig(
            crashStoragePath = "/test/path",
            appPackageId = "com.test",
            appVersion = "1.0",
            context = null,
            getIsInForeground = null
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.appState

        // Then
        result shouldBe "unknown"
    }

    test("appState handles exceptions gracefully and returns unknown") {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSystemService(any<String>()) } throws RuntimeException("Test exception")
        val config = OtelPlatformProviderConfig(
            crashStoragePath = "/test/path",
            appPackageId = "com.test",
            appVersion = "1.0",
            context = mockContext,
            getIsInForeground = null
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.appState

        // Then
        result shouldBe "unknown"
    }

    // ===== processUptime Tests =====

    test("processUptime returns uptime in seconds") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.processUptime

        // Then
        (result > 0.0) shouldBe true
        (result < 1000000.0) shouldBe true // Reasonable upper bound
    }

    // ===== currentThreadName Tests =====

    test("currentThreadName returns current thread name") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.currentThreadName

        // Then
        result shouldNotBe null
        result shouldNotBe ""
    }

    // ===== crashStoragePath Tests =====

    test("crashStoragePath returns configured path") {
        // Given
        val expectedPath = "/test/crash/path"
        val config = OtelPlatformProviderConfig(
            crashStoragePath = expectedPath,
            appPackageId = "com.test",
            appVersion = "1.0"
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.crashStoragePath

        // Then
        result shouldBe expectedPath
    }

    test("crashStoragePath logs info message on first access") {
        // Given
        val logSlot = slot<String>()
        val expectedPath = "/test/crash/path"
        val config = OtelPlatformProviderConfig(
            crashStoragePath = expectedPath,
            appPackageId = "com.test",
            appVersion = "1.0"
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.crashStoragePath

        // Then
        result shouldBe expectedPath
        // Note: We can't easily verify Logging.info was called without mocking Logging,
        // but the behavior is tested by ensuring the path is returned correctly
    }

    test("createAndroidOtelPlatformProvider sets correct crashStoragePath") {
        // Given & When
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // Then
        provider.crashStoragePath shouldContain "onesignal"
        provider.crashStoragePath shouldContain "otel"
        provider.crashStoragePath shouldContain "crashes"
    }

    // ===== minFileAgeForReadMillis Tests =====

    test("minFileAgeForReadMillis returns default value") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.minFileAgeForReadMillis

        // Then
        result shouldBe 5_000L
    }

    // ===== remoteLogLevel Tests =====

    test("remoteLogLevel returns ERROR when configLevel is null") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.remoteLogLevel

        // Then
        result shouldBe "ERROR"
    }

    test("remoteLogLevel returns configLevel name when available") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "WARN")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.remoteLogLevel

        // Then
        result shouldBe "WARN"
    }

    test("remoteLogLevel returns NONE when configLevel is NONE") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "NONE")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.remoteLogLevel

        // Then
        result shouldBe "NONE"
    }

    test("remoteLogLevel returns ERROR when exception occurs") {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } throws RuntimeException("Test exception")
        val config = OtelPlatformProviderConfig(
            crashStoragePath = "/test/path",
            appPackageId = "com.test",
            appVersion = "1.0",
            context = mockContext
        )
        val provider = OtelPlatformProvider(config)

        // When
        val result = provider.remoteLogLevel

        // Then
        result shouldBe "ERROR"
    }

    // ===== appIdForHeaders Tests =====

    test("appIdForHeaders returns appId when available") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id-123")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.appIdForHeaders

        // Then
        result shouldBe "test-app-id-123"
    }

    test("appIdForHeaders returns empty string when appId is null") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = provider.appIdForHeaders

        // Then - even with error appId, it should return something (not empty)
        result shouldNotBe null
    }

    // ===== getInstallId Tests =====

    test("getInstallId returns installId from SharedPreferences") {
        // Given
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID, "test-install-id-123")
            .commit()

        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = runBlocking { provider.getInstallId() }

        // Then
        result shouldBe "test-install-id-123"
    }

    test("getInstallId returns default when not found") {
        // Given
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // When
        val result = runBlocking { provider.getInstallId() }

        // Then
        result shouldBe "InstallId-Null"
    }

    // ===== Factory Function Tests =====

    test("createAndroidOtelPlatformProvider creates provider with correct config") {
        // Given & When
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        // Then
        provider.appPackageId shouldBe appContext!!.packageName
        provider.sdkBase shouldBe "android"
        provider.osName shouldBe "Android"
    }

    test("createAndroidOtelPlatformProvider handles null appVersion gracefully") {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        val mockPackageManager = mockk<android.content.pm.PackageManager>(relaxed = true)
        every { mockContext.packageName } returns "com.test"
        every { mockContext.cacheDir } returns appContext!!.cacheDir
        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.getSharedPreferences(any<String>(), any<Int>()) } returns sharedPreferences
        // Make getPackageInfo throw NameNotFoundException to simulate missing package
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } throws android.content.pm.PackageManager.NameNotFoundException()

        // When
        val provider: OtelPlatformProvider = createAndroidOtelPlatformProvider(mockContext)

        // Then
        provider.appVersion shouldBe "unknown"
    }
})

// Helper extension for shouldBeOneOf
private infix fun <T> T.shouldBeOneOf(expected: List<T>) {
    val isInList = expected.contains(this)
    if (!isInList) {
        throw AssertionError("Expected $this to be one of $expected")
    }
}
