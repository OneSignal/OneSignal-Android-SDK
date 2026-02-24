package com.onesignal.debug.internal.logging.otel.android

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.IDManager.LOCAL_PREFIX
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.user.internal.backend.IdentityConstants
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.json.JSONArray
import org.json.JSONObject
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace
import com.onesignal.user.internal.identity.IDENTITY_NAME_SPACE as identityNameSpace

@RobolectricTest
class OtelIdResolverTest : FunSpec({

    var appContext: Context? = null
    var sharedPreferences: SharedPreferences? = null

    // Helper function to ensure SharedPreferences data is written and verified
    fun writeAndVerifyConfigData(configArray: JSONArray) {
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit()

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }
    }

    // Helper function to ensure SharedPreferences identity data is written and verified
    fun writeAndVerifyIdentityData(identityArray: JSONArray) {
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
        editor.commit()

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, null)
        if (verifyData == null || verifyData != identityArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }
    }

    beforeAny {
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
            sharedPreferences = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        }
        // AGGRESSIVE CLEANUP: Clear ALL SharedPreferences before any test runs
        // This ensures clean state even if previous test classes left data behind
        sharedPreferences!!.edit().clear().commit()
        try {
            val otherPrefs = appContext!!.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
            otherPrefs.edit().clear().commit()
        } catch (e: Exception) {
            // Ignore any errors during cleanup
        }
    }

    beforeEach {
        // Ensure appContext is initialized
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
        }

        // Get a FRESH SharedPreferences instance for each test to avoid caching issues
        // This ensures we're not reading stale data from previous tests
        sharedPreferences = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)

        // AGGRESSIVE CLEANUP: Clear ALL SharedPreferences to ensure complete isolation
        sharedPreferences!!.edit().clear().commit()

        // Also clear any other potential SharedPreferences files
        try {
            val otherPrefs = appContext!!.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
            otherPrefs.edit().clear().commit()
        } catch (e: Exception) {
            // Ignore any errors during cleanup
        }
    }

    afterEach {
        // Clean up after each test
        sharedPreferences!!.edit().clear().commit()

        // Also clear any other potential SharedPreferences files
        try {
            val otherPrefs = appContext!!.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
            otherPrefs.edit().clear().commit()
        } catch (e: Exception) {
            // Ignore any errors during cleanup
        }
    }

    afterSpec {
        // Final cleanup after all tests in this spec
        if (appContext != null) {
            try {
                val prefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                prefs.edit().clear().commit()

                val otherPrefs = appContext!!.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
                otherPrefs.edit().clear().commit()
            } catch (e: Exception) {
                // Ignore any errors during cleanup
            }
        }
    }

    // ===== resolveAppId Tests =====

    test("resolveAppId returns appId from ConfigModelStore when available") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id-123")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveAppId()

        // Then
        result shouldBe "test-app-id-123"
    }

    test("resolveAppId returns empty string appId as null and falls back to legacy") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .putString(PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, "legacy-app-id")
            .commit()

        // Ensure commit is complete before creating resolver
        Thread.sleep(10)

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveAppId()

        // Then
        result shouldBe "legacy-app-id"
    }

    test("resolveAppId falls back to legacy SharedPreferences when ConfigModelStore has no appId") {
        // Given
        val configModel = JSONObject() // No appId field
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .putString(PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, "legacy-app-id")
            .commit()

        // Ensure commit is complete before creating resolver
        Thread.sleep(10)

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveAppId()

        // Then
        result shouldBe "legacy-app-id"
    }

    test("resolveAppId returns error appId when ConfigModelStore is null") {
        // Given
        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveAppId()

        // Then - ERROR_APP_ID_PREFIX_NO_CONFIG_STORE
        result shouldBe "e1100000-0000-4000-a000-000000000002"
    }

    test("resolveAppId returns error appId when ConfigModelStore is empty array") {
        // Given
        val configArray = JSONArray() // Empty array
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveAppId()

        // Then - ERROR_APP_ID_PREFIX_NO_APPID_IN_CONFIG_STORE
        result shouldBe "e1100000-0000-4000-a000-000000000003"
    }

    test("resolveAppId returns error appId when context is null") {
        // Given
        val resolver = OtelIdResolver(null)

        // When
        val result = resolver.resolveAppId()

        // Then - ERROR_APP_ID_PREFIX_NO_CONTEXT
        result shouldBe "e1100000-0000-4000-a000-000000000004"
    }

    test("resolveAppId handles JSON parsing exceptions gracefully") {
        // Given
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, "invalid-json")
            .commit()

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveAppId()

        // Then - JSON parse error results in null configModel, so ERROR_APP_ID_PREFIX_NO_CONFIG_STORE
        result shouldBe "e1100000-0000-4000-a000-000000000002"
    }

    // ===== resolveOnesignalId Tests =====

    test("resolveOnesignalId returns onesignalId from IdentityModelStore when available") {
        // Given
        val identityModel = JSONObject().apply {
            put(IdentityConstants.ONESIGNAL_ID, "test-onesignal-id-123")
        }
        val identityArray = JSONArray().apply {
            put(identityModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, null)
        if (verifyData == null || verifyData != identityArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe "test-onesignal-id-123"
    }

    test("resolveOnesignalId returns null when onesignalId is empty string") {
        // Given
        val identityModel = JSONObject().apply {
            put(IdentityConstants.ONESIGNAL_ID, "")
        }
        val identityArray = JSONArray().apply {
            put(identityModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, null)
        if (verifyData == null || verifyData != identityArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe null
    }

    test("resolveOnesignalId returns null when onesignalId is a local ID") {
        // Given
        val localId = "${LOCAL_PREFIX}test-id"
        val identityModel = JSONObject().apply {
            put(IdentityConstants.ONESIGNAL_ID, localId)
        }
        val identityArray = JSONArray().apply {
            put(identityModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, null)
        if (verifyData == null || verifyData != identityArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe null
    }

    test("resolveOnesignalId returns null when IdentityModelStore has no onesignalId field") {
        // Given
        val identityModel = JSONObject() // No ONESIGNAL_ID field
        val identityArray = JSONArray().apply {
            put(identityModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, null)
        if (verifyData == null || verifyData != identityArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe null
    }

    test("resolveOnesignalId returns null when IdentityModelStore is null") {
        // Given
        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe null
    }

    test("resolveOnesignalId returns null when IdentityModelStore is empty array") {
        // Given
        val identityArray = JSONArray() // Empty array
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, identityArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, null)
        if (verifyData == null || verifyData != identityArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe null
    }

    test("resolveOnesignalId handles JSON parsing exceptions gracefully") {
        // Given
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + identityNameSpace, "invalid-json")
            .commit()

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveOnesignalId()

        // Then
        result shouldBe null
    }

    // ===== resolvePushSubscriptionId Tests =====

    test("resolvePushSubscriptionId returns pushSubscriptionId from ConfigModelStore when available") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::pushSubscriptionId.name, "test-push-sub-id-123")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolvePushSubscriptionId()

        // Then
        result shouldBe "test-push-sub-id-123"
    }

    test("resolvePushSubscriptionId returns null when pushSubscriptionId is empty string") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::pushSubscriptionId.name, "")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolvePushSubscriptionId()

        // Then
        result shouldBe null
    }

    test("resolvePushSubscriptionId returns null when pushSubscriptionId is a local ID") {
        // Given
        val localId = "${LOCAL_PREFIX}test-id"
        val configModel = JSONObject().apply {
            put(ConfigModel::pushSubscriptionId.name, localId)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolvePushSubscriptionId()

        // Then
        result shouldBe null
    }

    test("resolvePushSubscriptionId returns null when ConfigModelStore has no pushSubscriptionId field") {
        // Given
        val configModel = JSONObject() // No pushSubscriptionId field
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolvePushSubscriptionId()

        // Then
        result shouldBe null
    }

    test("resolvePushSubscriptionId returns null when ConfigModelStore is null") {
        // Given
        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolvePushSubscriptionId()

        // Then
        result shouldBe null
    }

    test("resolvePushSubscriptionId handles JSON parsing exceptions gracefully") {
        // Given
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, "invalid-json")
            .commit()

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolvePushSubscriptionId()

        // Then
        result shouldBe null
    }

    // ===== resolveRemoteLoggingEnabled Tests =====
    // Enabled is derived from presence of a valid logLevel:
    //   "logging_config": {} → disabled (not on allowlist)
    //   "logging_config": {"log_level": "ERROR"} → enabled (on allowlist)

    test("resolveRemoteLoggingEnabled returns true when logLevel is ERROR") {
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "ERROR")
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

        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe true
    }

    test("resolveRemoteLoggingEnabled returns true when logLevel is WARN") {
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

        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe true
    }

    test("resolveRemoteLoggingEnabled returns false when logLevel is NONE") {
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

        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe false
    }

    test("resolveRemoteLoggingEnabled returns false when logLevel field is missing (empty logging_config)") {
        val remoteLoggingParams = JSONObject()
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe false
    }

    test("resolveRemoteLoggingEnabled returns false when remoteLoggingParams is missing") {
        val configModel = JSONObject()
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe false
    }

    test("resolveRemoteLoggingEnabled returns false when no config exists") {
        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe false
    }

    test("resolveRemoteLoggingEnabled returns false when logLevel is invalid") {
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "INVALID_LEVEL")
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

        val resolver = OtelIdResolver(appContext!!)
        resolver.resolveRemoteLoggingEnabled() shouldBe false
    }

    // ===== resolveRemoteLogLevel Tests =====

    test("resolveRemoteLogLevel returns LogLevel from ConfigModelStore when available") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "ERROR")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe LogLevel.ERROR
    }

    test("resolveRemoteLogLevel returns LogLevel case-insensitively") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "warn")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe LogLevel.WARN
    }

    test("resolveRemoteLogLevel returns null when logLevel field is missing") {
        // Given
        val remoteLoggingParams = JSONObject() // No logLevel field
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe null
    }

    test("resolveRemoteLogLevel returns null when remoteLoggingParams field is missing") {
        // Given
        val configModel = JSONObject() // No remoteLoggingParams field
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe null
    }

    test("resolveRemoteLogLevel returns null when logLevel is invalid") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "INVALID_LEVEL")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe null
    }

    test("resolveRemoteLogLevel returns null when ConfigModelStore is null") {
        // Given
        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe null
    }

    test("resolveRemoteLogLevel handles JSON parsing exceptions gracefully") {
        // Given
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, "invalid-json")
            .commit()

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveRemoteLogLevel()

        // Then
        result shouldBe null
    }

    // ===== resolveInstallId Tests =====

    test("resolveInstallId returns installId from SharedPreferences when available") {
        // Given
        sharedPreferences!!.edit()
            .putString(PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID, "test-install-id-123")
            .commit()

        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveInstallId()

        // Then
        result shouldBe "test-install-id-123"
    }

    test("resolveInstallId returns default InstallId-Null when not found") {
        // Given
        val resolver = OtelIdResolver(appContext!!)

        // When
        val result = resolver.resolveInstallId()

        // Then
        result shouldBe "InstallId-Null"
    }

    test("resolveInstallId returns InstallId-NotFound when exception occurs") {
        // Given
        val mockContext = mockk<Context>(relaxed = true)
        val mockSharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } throws RuntimeException("Test exception")

        val resolver = OtelIdResolver(mockContext)

        // When
        val result = resolver.resolveInstallId()

        // Then
        result shouldBe "InstallId-NotFound"
    }

    // ===== Caching Tests =====

    test("cachedConfigModel is reused across multiple resolve calls") {
        // Given
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id")
            put(ConfigModel::pushSubscriptionId.name, "test-push-id")
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        // Write data and ensure it's committed
        val editor = sharedPreferences!!.edit()
        editor.putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
        editor.commit() // Use commit() to ensure synchronous write

        // Get a fresh SharedPreferences instance to ensure we read the latest data
        val freshPrefs = appContext!!.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
        val verifyData = freshPrefs.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, null)
        if (verifyData == null || verifyData != configArray.toString()) {
            throw AssertionError("Failed to write SharedPreferences data - test isolation issue")
        }

        val resolver = OtelIdResolver(appContext!!)

        // When - resolve multiple IDs
        val appId1 = resolver.resolveAppId()
        val pushId1 = resolver.resolvePushSubscriptionId()
        val appId2 = resolver.resolveAppId()
        val pushId2 = resolver.resolvePushSubscriptionId()

        // Then - should return same values (cached)
        appId1 shouldBe "test-app-id"
        pushId1 shouldBe "test-push-id"
        appId2 shouldBe "test-app-id"
        pushId2 shouldBe "test-push-id"
    }
})
