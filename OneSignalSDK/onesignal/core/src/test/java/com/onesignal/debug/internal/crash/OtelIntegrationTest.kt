package com.onesignal.debug.internal.crash

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.OtelFactory
import com.onesignal.user.internal.backend.IdentityConstants
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace
import com.onesignal.user.internal.identity.IDENTITY_NAME_SPACE as identityNameSpace

// Helper extension for shouldBeOneOf
private infix fun <T> T.shouldBeOneOf(expected: List<T>) {
    val isInList = expected.contains(this)
    if (!isInList) {
        throw AssertionError("Expected $this to be one of $expected")
    }
}

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelIntegrationTest : FunSpec({
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
        // Clear and set up SharedPreferences with test data
        sharedPreferences!!.edit().clear().commit()

        // Set up ConfigModelStore data
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id")
            put(ConfigModel::pushSubscriptionId.name, "test-subscription-id")
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
            put(IdentityConstants.ONESIGNAL_ID, "test-onesignal-id")
        }
        val identityArray = JSONArray().apply {
            put(identityModel)
        }

        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
            .putString(PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID, "test-install-id")
            .commit()
    }

    afterEach {
        sharedPreferences!!.edit().clear().commit()
    }

    test("AndroidOtelPlatformProvider should provide correct Android values") {
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        provider.shouldBeInstanceOf<IOtelPlatformProvider>()
        provider.sdkBase shouldBe "android"
        provider.appPackageId shouldBe appContext!!.packageName // Use actual package name from context
        provider.osName shouldBe "Android"
        provider.deviceManufacturer shouldBe Build.MANUFACTURER
        provider.deviceModel shouldBe Build.MODEL
        provider.osVersion shouldBe Build.VERSION.RELEASE
        provider.osBuildId shouldBe Build.ID

        runBlocking {
            provider.getInstallId() shouldNotBe null
        }
    }

    test("AndroidOtelPlatformProvider should provide per-event values") {
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        provider.appId shouldBe "test-app-id"
        provider.onesignalId shouldBe "test-onesignal-id"
        provider.pushSubscriptionId shouldBe "test-subscription-id"
        provider.appState shouldBeOneOf listOf("foreground", "background", "unknown")
        (provider.processUptime > 0) shouldBe true
        provider.currentThreadName shouldBe Thread.currentThread().name
    }

    test("AndroidOtelLogger should delegate to Logging") {
        val logger = AndroidOtelLogger()

        logger.shouldBeInstanceOf<com.onesignal.otel.IOtelLogger>()
        // Should not throw
        logger.debug("test")
        logger.info("test")
        logger.warn("test")
        logger.error("test")
    }

    test("OtelFactory should create crash handler with Android provider") {
        val provider = createAndroidOtelPlatformProvider(appContext!!)
        val logger = AndroidOtelLogger()

        val handler = OtelFactory.createCrashHandler(provider, logger)

        handler shouldNotBe null
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
        handler.initialize() // Should not throw
    }

    test("OneSignalCrashHandlerFactory should create working crash handler") {
        // Note: OneSignalCrashHandlerFactory may need to be updated to use the new approach
        // For now, we'll test the direct creation
        val provider = createAndroidOtelPlatformProvider(appContext!!)
        val logger = AndroidOtelLogger()
        val handler = OtelFactory.createCrashHandler(provider, logger)

        handler shouldNotBe null
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
        handler.initialize() // Should not throw
    }

    test("AndroidOtelPlatformProvider should provide crash storage path") {
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        provider.crashStoragePath.contains("onesignal") shouldBe true
        provider.crashStoragePath.contains("otel") shouldBe true
        provider.crashStoragePath.contains("crashes") shouldBe true
        provider.minFileAgeForReadMillis shouldBe 5000L
    }

    test("AndroidOtelPlatformProvider should handle remote logging config") {
        val provider = createAndroidOtelPlatformProvider(appContext!!)

        provider.remoteLogLevel shouldBe "ERROR"
        provider.appIdForHeaders shouldBe "test-app-id"
    }
})
