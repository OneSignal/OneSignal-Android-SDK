package com.onesignal.internal

import android.content.Context
import com.onesignal.common.AndroidUtils
import com.onesignal.common.IDManager
import com.onesignal.common.services.IServiceProvider
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProvider
import com.onesignal.debug.internal.logging.otel.android.OtelPlatformProviderConfig
import com.onesignal.debug.internal.logging.otel.android.createAndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelOpenTelemetryRemote
import com.onesignal.otel.OtelFactory
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModelStore
import org.json.JSONArray

/**
 * Helper object for OneSignal initialization tasks.
 * Extracted from OneSignalImp to reduce class size and improve maintainability.
 */
internal object OneSignalCrashLogInit {
    @Suppress("TooGenericExceptionCaught")
    fun initializeCrashHandler(
        context: Context,
        services: IServiceProvider,
    ) {
        try {
            Logging.info("OneSignal: Initializing crash handler early...")
            // Use minimal dependencies - only primitive values and installId provider
            // This makes crash handler completely independent of service architecture
            // Context is passed directly from initEssentials, not through ApplicationService
            val crashStoragePath = context.cacheDir.path + java.io.File.separator +
                "onesignal" + java.io.File.separator +
                "otel" + java.io.File.separator +
                "crashes"
            val appPackageId = context.packageName
            val appVersion = try {
                AndroidUtils.getAppVersion(context) ?: "unknown"
            } catch (e: Exception) {
                Logging.warn("OneSignal: Failed to get app version for crash handler: ${e.message}, using 'unknown'")
                "unknown"
            }

            Logging.info("OneSignal: Creating crash handler with minimal dependencies...")

            // Initialize crash handler immediately with minimal setup (non-blocking)
            // Service getters check services dynamically when called - if services are available
            // at crash time, they'll be used; otherwise falls back to SharedPreferences
            // This ensures fast initialization while still using services when available

            // Helper to get ConfigModelStore (used by multiple getters, but called lazily at crash time)
            val getConfigModelStore: () -> ConfigModelStore? = {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    services.getServiceOrNull(ConfigModelStore::class.java)
                } catch (e: Exception) {
                    null
                }
            }

            // Create platform provider with lambda getters that check services dynamically
            // If services are available at crash time, use them; otherwise fall back to SharedPreferences
            val platformProvider = OtelPlatformProvider(
                OtelPlatformProviderConfig(
                    crashStoragePath = crashStoragePath,
                    appPackageId = appPackageId,
                    appVersion = appVersion,
                    getInstallIdProvider = {
                        @Suppress("TooGenericExceptionCaught")
                        try {
                            val installIdService = services.getServiceOrNull(IInstallIdService::class.java)
                            installIdService?.getId()?.toString() ?: ""
                        } catch (e: Exception) {
                            Logging.warn("OneSignal: Failed to get installId for crash handler: ${e.message}, using empty string")
                            ""
                        }
                    },
                    context = context,
                    // Dynamic service getters - check services when called (at crash time)
                    // If services are available, use them; otherwise fall back to SharedPreferences
                    getAppId = {
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            // First: try to get from service (ConfigModelStore) if available
                            getConfigModelStore()?.model?.appId ?: run {
                                // Second: try to get from app's SharedPreferences (like SharedPreferenceUtil.getOneSignalAppId)
                                // This reads from the app's own SharedPreferences where the app ID might be cached
                                try {
                                    val appPrefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
                                    appPrefs.getString("OS_APP_ID_SHARED_PREF", null)?.takeIf { it.isNotEmpty() }
                                } catch (e: Exception) {
                                    null
                                }
                            } ?: run {
                                // Third: fall back to legacy OneSignal SharedPreferences
                                context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                                    ?.getString(PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, null)
                                    ?.takeIf { it.isNotEmpty() }
                            } ?: "unknown" // Fourth: if all checks are empty, return "unknown"
                        } catch (e: Exception) {
                            "unknown" // If there's an error, return "unknown"
                        }
                    },
                    getOnesignalId = {
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            val identityModelStore = services.getServiceOrNull(IdentityModelStore::class.java)
                            val onesignalId = identityModelStore?.model?.onesignalId
                            onesignalId?.takeIf { !IDManager.isLocalId(it) } ?: run {
                                // Fall back to SharedPreferences - try MODEL_STORE_identity first, then legacy
                                val prefs = context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                                val identityStoreJson = prefs?.getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "identity", null)
                                if (identityStoreJson != null) {
                                    @Suppress("SwallowedException")
                                    try {
                                        val jsonArray = JSONArray(identityStoreJson)
                                        if (jsonArray.length() > 0) {
                                            val identityModelJson = jsonArray.getJSONObject(0)
                                            val onesignalIdFromPrefs = identityModelJson.optString(IdentityConstants.ONESIGNAL_ID, null)
                                            onesignalIdFromPrefs?.takeIf { it.isNotEmpty() && !IDManager.isLocalId(it) }
                                        } else {
                                            null
                                        }
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            } ?: run {
                                // Final fallback to legacy player ID
                                context.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                                    ?.getString(PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID, null)
                                    ?.takeIf { !IDManager.isLocalId(it) }
                            }
                        } catch (e: Exception) {
                            null
                        }
                    },
                    getPushSubscriptionId = {
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            val pushSubscriptionId = getConfigModelStore()?.model?.pushSubscriptionId
                            pushSubscriptionId?.takeIf { !IDManager.isLocalId(pushSubscriptionId) }
                        } catch (e: Exception) {
                            null
                        }
                    },
                    getIsInForeground = {
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            val applicationService = services.getServiceOrNull(IApplicationService::class.java)
                            applicationService?.isInForeground
                        } catch (e: Exception) {
                            null
                        }
                    },
                    getRemoteLogLevel = {
                        @Suppress("TooGenericExceptionCaught", "SwallowedException")
                        try {
                            getConfigModelStore()?.model?.remoteLoggingParams?.logLevel
                        } catch (e: Exception) {
                            null
                        }
                    },
                )
            )

            // Create crash handler directly (non-blocking, doesn't require services upfront)
            val logger = AndroidOtelLogger()
            val crashHandler: IOtelCrashHandler = OtelFactory.createCrashHandler(platformProvider, logger)

            Logging.info("OneSignal: Crash handler created, initializing...")
            crashHandler.initialize()

            // Log crash storage location for debugging
            Logging.info("OneSignal: ✅ Crash handler initialized successfully and ready to capture crashes")
            Logging.info("OneSignal: 📁 Crash logs will be stored at: $crashStoragePath")
            Logging.info("OneSignal: 💡 To view crash logs, use: adb shell run-as ${context.packageName} ls -la $crashStoragePath")
        } catch (e: Exception) {
            // If crash handler initialization fails, log it but don't crash
            Logging.error("OneSignal: Failed to initialize crash handler: ${e.message}", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun initializeOtelLogging(
        applicationService: IApplicationService,
        services: IServiceProvider,
    ) {
        // Initialize Otel logging asynchronously to avoid blocking initialization
        // Remote logging is not critical for crashes, so it's safe to do this in the background
        suspendifyOnIO {
            try {
                // Get dependencies needed for Otel logging (non-blocking, uses getServiceOrNull)
                val installIdService = services.getServiceOrNull(IInstallIdService::class.java)
                val configModelStore = services.getServiceOrNull(ConfigModelStore::class.java)
                val identityModelStore = services.getServiceOrNull(IdentityModelStore::class.java)

                // If services aren't available yet, skip initialization (will be retried later if needed)
                if (installIdService == null || configModelStore == null || identityModelStore == null) {
                    Logging.debug("OneSignal: Services not yet available for Otel logging, skipping initialization")
                    return@suspendifyOnIO
                }

                val platformProvider = createAndroidOtelPlatformProvider(
                    applicationService,
                    installIdService,
                    configModelStore,
                    identityModelStore
                )

                // Get the remote log level as string (defaults to "ERROR" if null, "NONE" if explicitly set)
                val remoteLogLevelStr = platformProvider.remoteLogLevel

                // Check if remote logging is enabled (not NONE)
                val isRemoteLoggingEnabled = remoteLogLevelStr != null && remoteLogLevelStr != "NONE"

                if (isRemoteLoggingEnabled) {
                    Logging.info("OneSignal: Remote logging enabled at level $remoteLogLevelStr, initializing Otel logging integration...")
                    val remoteTelemetry: IOtelOpenTelemetryRemote = OtelFactory.createRemoteTelemetry(platformProvider)

                    // Parse the log level string to LogLevel enum for comparison
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    val remoteLogLevel = try {
                        LogLevel.valueOf(remoteLogLevelStr)
                    } catch (e: Exception) {
                        LogLevel.ERROR // Default to ERROR on parse error
                    }

                    // Create a function that checks if a log level should be sent remotely
                    // - If remoteLogLevel is null: default to ERROR (send ERROR and above)
                    // - If remoteLogLevel is NONE: don't send anything (shouldn't reach here, but handle it)
                    // - Otherwise: send logs at that level and above
                    val shouldSendLogLevel: (LogLevel) -> Boolean = { level ->
                        when {
                            remoteLogLevel == LogLevel.NONE -> false // Don't send anything
                            else -> level >= remoteLogLevel // Send at configured level and above
                        }
                    }

                    // Inject Otel telemetry into Logging class
                    Logging.setOtelTelemetry(remoteTelemetry, shouldSendLogLevel)
                    Logging.info("OneSignal: ✅ Otel logging integration initialized - logs at level $remoteLogLevelStr and above will be sent to remote server")
                } else {
                    Logging.debug("OneSignal: Remote logging disabled (level: $remoteLogLevelStr), skipping Otel logging integration")
                }
            } catch (e: Exception) {
                // If Otel logging initialization fails, log it but don't crash
                Logging.warn("OneSignal: Failed to initialize Otel logging: ${e.message}", e)
            }
        }
    }
}
