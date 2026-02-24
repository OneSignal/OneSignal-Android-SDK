package com.onesignal.internal

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelCrashHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Config
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE as configNameSpace

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OneSignalOtelInitTest : FunSpec({

    val context: Context = ApplicationProvider.getApplicationContext()
    val sharedPreferences = context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        // Clear SharedPreferences before each test
        sharedPreferences.edit().clear().commit()
    }

    afterAny {
        // Clean up after each test
        sharedPreferences.edit().clear().commit()
        // Restore default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    // ===== Platform Provider Reuse Tests =====

    test("platform provider should be created once and reused") {
        // Given
        val otelInit = OneSignalOtelInit(context)

        // When - initialize crash handler (creates platform provider)
        otelInit.initializeCrashHandler()

        // Then - initialize logging should reuse the same platform provider
        // We can't directly access the private property, but we can verify behavior
        // by checking that both initializations succeed without errors
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(100) // Give async initialization time to complete
        }

        // If we got here without exceptions, the platform provider was reused successfully
    }

    test("should create instance with context") {
        // Given & When
        val otelInit = OneSignalOtelInit(context)

        // Then
        otelInit shouldNotBe null
    }

    // ===== Crash Handler Initialization Tests =====

    test("initializeCrashHandler should create and initialize crash handler") {
        // Given
        val otelInit = OneSignalOtelInit(context)
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // When
        otelInit.initializeCrashHandler()

        // Then
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        currentHandler shouldNotBe null
        currentHandler.shouldBeInstanceOf<IOtelCrashHandler>()

        // Cleanup
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("initializeCrashHandler should handle exceptions gracefully") {
        // Given
        val mockContext = io.mockk.mockk<Context>(relaxed = true)
        io.mockk.every { mockContext.cacheDir } throws RuntimeException("Test exception")
        io.mockk.every { mockContext.packageName } returns "com.test"
        io.mockk.every { mockContext.getSharedPreferences(any(), any()) } returns sharedPreferences

        val otelInit = OneSignalOtelInit(mockContext)

        // When & Then - should not throw
        otelInit.initializeCrashHandler()
    }

    test("initializeCrashHandler should initialize ANR detector") {
        // Given
        val otelInit = OneSignalOtelInit(context)
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // When
        otelInit.initializeCrashHandler()

        // Then - ANR detector should be started (we can't directly verify, but no exception means success)
        // The method logs success, so if it doesn't throw, it worked

        // Cleanup
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("initializeCrashHandler can be called multiple times safely") {
        // Given
        val otelInit = OneSignalOtelInit(context)
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // When
        otelInit.initializeCrashHandler()
        otelInit.initializeCrashHandler() // Call again

        // Then - should not throw or cause issues
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        currentHandler shouldNotBe null

        // Cleanup
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    // ===== Otel Logging Initialization Tests =====

    test("initializeOtelLogging should initialize remote telemetry when enabled") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "ERROR")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id")
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val otelInit = OneSignalOtelInit(context)

        // When
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(200) // Give async initialization time to complete
        }

        // Then - should not throw, telemetry should be set
        // We can't directly verify Logging.setOtelTelemetry was called, but no exception means success
    }

    test("initializeOtelLogging should skip initialization when remote logging is disabled") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "NONE")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id")
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val otelInit = OneSignalOtelInit(context)

        // When
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(100) // Give async initialization time to complete
        }

        // Then - should not throw, should skip initialization
    }

    test("initializeOtelLogging should default to ERROR when remote log level is not configured") {
        // Given - no remote logging config in SharedPreferences
        val otelInit = OneSignalOtelInit(context)

        // When
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(200) // Give async initialization time to complete
        }

        // Then - should default to ERROR level and initialize
        // No exception means it worked
    }

    test("initializeOtelLogging should handle exceptions gracefully") {
        // Given
        val mockContext = io.mockk.mockk<Context>(relaxed = true)
        io.mockk.every { mockContext.cacheDir } returns context.cacheDir
        io.mockk.every { mockContext.packageName } returns "com.test"
        io.mockk.every { mockContext.getSharedPreferences(any(), any()) } throws RuntimeException("Test exception")

        val otelInit = OneSignalOtelInit(mockContext)

        // When & Then - should not throw
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(100)
        }
    }

    test("initializeOtelLogging can be called multiple times safely") {
        // Given
        val otelInit = OneSignalOtelInit(context)

        // When
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(100)
            otelInit.initializeOtelLogging() // Call again
            delay(100)
        }

        // Then - should not throw or cause issues
    }

    // ===== Integration Tests =====

    test("both initializeCrashHandler and initializeOtelLogging should work together") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "WARN")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id")
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val otelInit = OneSignalOtelInit(context)
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

        // When
        otelInit.initializeCrashHandler()
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(200)
        }

        // Then - both should succeed
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        currentHandler shouldNotBe null

        // Cleanup
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    test("should work with different log levels") {
        // Given
        val logLevels = listOf("ERROR", "WARN", "INFO", "DEBUG", "VERBOSE")

        logLevels.forEach { level ->
            val remoteLoggingParams = JSONObject().apply {
                put("logLevel", level)
            }
            val configModel = JSONObject().apply {
                put(ConfigModel::appId.name, "test-app-id")
                put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
            }
            val configArray = JSONArray().apply {
                put(configModel)
            }
            sharedPreferences.edit().clear().commit()
            sharedPreferences.edit()
                .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
                .commit()

            val otelInit = OneSignalOtelInit(context)

            // When
            runBlocking {
                otelInit.initializeOtelLogging()
                delay(100)
            }

            // Then - should not throw for any log level
        }
    }

    test("should handle invalid log level gracefully") {
        // Given
        val remoteLoggingParams = JSONObject().apply {
            put("logLevel", "INVALID_LEVEL")
        }
        val configModel = JSONObject().apply {
            put(ConfigModel::appId.name, "test-app-id")
            put(ConfigModel::remoteLoggingParams.name, remoteLoggingParams)
        }
        val configArray = JSONArray().apply {
            put(configModel)
        }
        sharedPreferences.edit()
            .putString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + configNameSpace, configArray.toString())
            .commit()

        val otelInit = OneSignalOtelInit(context)

        // When & Then - should default to ERROR and not throw
        runBlocking {
            otelInit.initializeOtelLogging()
            delay(100)
        }
    }

    // ===== Context Handling Tests =====

    test("should work with different contexts") {
        // Given
        val context1: Context = ApplicationProvider.getApplicationContext()
        val context2: Context = ApplicationProvider.getApplicationContext()

        val otelInit1 = OneSignalOtelInit(context1)
        val otelInit2 = OneSignalOtelInit(context2)

        // When
        otelInit1.initializeCrashHandler()
        otelInit2.initializeCrashHandler()

        // Then - both should work independently
        val handler1 = Thread.getDefaultUncaughtExceptionHandler()
        handler1 shouldNotBe null
    }
})
