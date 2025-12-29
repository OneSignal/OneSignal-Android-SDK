package com.onesignal.debug.internal.logging.otel.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelPlatformProvider

/**
 * Configuration for AndroidOtelPlatformProvider.
 * Groups parameters to avoid LongParameterList detekt issue.
 */
internal data class AndroidOtelPlatformProviderConfig(
    val crashStoragePath: String,
    val appPackageId: String,
    val appVersion: String,
    val getInstallIdProvider: suspend () -> String,
    val context: Context? = null,
    val getAppId: (() -> String?)? = null,
    val getOnesignalId: (() -> String?)? = null,
    val getPushSubscriptionId: (() -> String?)? = null,
    val getIsInForeground: (() -> Boolean?)? = null,
    val getRemoteLogLevel: (() -> com.onesignal.debug.LogLevel?)? = null,
)

/**
 * Android-specific implementation of IOtelPlatformProvider.
 * This provider can work with or without full service dependencies, making it flexible for both
 * early crash handler initialization and full remote logging scenarios.
 *
 * If Context is provided, it will attempt to retrieve additional metadata from SharedPreferences
 * and system services, falling back to null/defaults if unavailable.
 *
 * Optional service getters can be provided to retrieve values from services if available:
 * - getAppId: () -> String? - Get appId from ConfigModelStore if available
 * - getOnesignalId: () -> String? - Get onesignalId from IdentityModelStore if available
 * - getPushSubscriptionId: () -> String? - Get pushSubscriptionId from ConfigModelStore if available
 * - getIsInForeground: () -> Boolean? - Get foreground state from ApplicationService if available
 * - getRemoteLogLevel: () -> LogLevel? - Get remote logging level from ConfigModelStore if available
 */
internal class AndroidOtelPlatformProvider(
    config: AndroidOtelPlatformProviderConfig,
) : IOtelPlatformProvider {
    override val appPackageId: String = config.appPackageId
    override val appVersion: String = config.appVersion
    private val getInstallIdProvider: suspend () -> String = config.getInstallIdProvider
    private val context: Context? = config.context
    private val getAppId: (() -> String?)? = config.getAppId
    private val getOnesignalId: (() -> String?)? = config.getOnesignalId
    private val getPushSubscriptionId: (() -> String?)? = config.getPushSubscriptionId
    private val getIsInForeground: (() -> Boolean?)? = config.getIsInForeground
    private val getRemoteLogLevel: (() -> com.onesignal.debug.LogLevel?)? = config.getRemoteLogLevel

    // Top-level attributes (static, calculated once)
    override suspend fun getInstallId(): String = getInstallIdProvider()

    override val sdkBase: String = "android"

    override val sdkBaseVersion: String = OneSignalUtils.sdkVersion

    override val deviceManufacturer: String = Build.MANUFACTURER

    override val deviceModel: String = Build.MODEL

    override val osName: String = "Android"

    override val osVersion: String = Build.VERSION.RELEASE

    override val osBuildId: String = Build.ID

    override val sdkWrapper: String? = OneSignalWrapper.sdkType

    override val sdkWrapperVersion: String? = OneSignalWrapper.sdkVersion

    // Per-event attributes (dynamic, calculated per event)
    // Try to retrieve from services or SharedPreferences if available, fall back to null
    override val appId: String?
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        get() = try {
            // First try to get from service if available
            getAppId?.invoke() ?: run {
                // Fall back to SharedPreferences and pick up at least Legacy Id if it exists
                context?.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                    ?.getString(PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, null)
            }
        } catch (e: Exception) {
            null
        }

    override val onesignalId: String?
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        get() = try {
            // First try to get from service if available
            getOnesignalId?.invoke()?.takeIf { !IDManager.isLocalId(it) } ?: run {
                // Fall back to SharedPreferences and pick up at least Legacy Id if it exists
                context?.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                    ?.getString(PreferenceOneSignalKeys.PREFS_LEGACY_PLAYER_ID, null)
                    ?.takeIf { !IDManager.isLocalId(it) }
            }
        } catch (e: Exception) {
            null
        }

    override val pushSubscriptionId: String?
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        get() = try {
            getPushSubscriptionId?.invoke()?.takeIf { !IDManager.isLocalId(it) }
        } catch (e: Exception) {
            null
        }

    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/android/
    override val appState: String
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        get() = try {
            // Try to get from ApplicationService if available
            getIsInForeground?.invoke()?.let { isForeground ->
                if (isForeground) "foreground" else "background"
            } ?: run {
                // Fall back to ActivityManager if Context is available
                context?.let { ctx ->
                    @Suppress("TooGenericExceptionCaught", "SwallowedException")
                    try {
                        val activityManager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                        val runningAppProcesses = activityManager?.runningAppProcesses
                        val currentProcess = runningAppProcesses?.find { it.pid == android.os.Process.myPid() }
                        when (currentProcess?.importance) {
                            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "foreground"
                            else -> "background"
                        }
                    } catch (e: Exception) {
                        "unknown"
                    }
                } ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }

    // https://opentelemetry.io/docs/specs/semconv/system/process-metrics/#metric-processuptime
    override val processUptime: Double
        get() = android.os.SystemClock.uptimeMillis() / 1_000.0 // Use SystemClock directly

    // https://opentelemetry.io/docs/specs/semconv/general/attributes/#general-thread-attributes
    override val currentThreadName: String
        get() = Thread.currentThread().name

    // Crash-specific configuration
    // Store crashStoragePath privately since we need a custom getter that logs
    private val _crashStoragePath: String = config.crashStoragePath

    override val crashStoragePath: String
        get() {
            // Log the path on first access so developers know where to find crash logs
            Logging.info("OneSignal: Crash logs stored at: $_crashStoragePath")
            return _crashStoragePath
        }

    override val minFileAgeForReadMillis: Long = 5_000

    // Remote logging configuration
    /**
     * The minimum log level to send remotely as a string.
     * - If remote config log level is populated and valid: use that level
     * - If remote config is null or unavailable: default to "ERROR" (bare minimum for client-side)
     * - If remote config is explicitly NONE: return "NONE" (no logs including errors)
     */
    override val remoteLogLevel: String?
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        get() = try {
            val configLevel = getRemoteLogLevel?.invoke()
            when {
                // Remote config is populated and working well - use whatever is sent from there
                configLevel != null && configLevel != com.onesignal.debug.LogLevel.NONE -> configLevel.name
                // Explicitly NONE means no logging (including errors)
                configLevel == com.onesignal.debug.LogLevel.NONE -> "NONE"
                // Remote config not available - default to ERROR as bare minimum
                else -> "ERROR"
            }
        } catch (e: Exception) {
            // If there's an error accessing config, default to ERROR as bare minimum
            "ERROR"
        }

    override val appIdForHeaders: String
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        get() = try {
            // Try to get appId for headers (same logic as appId property)
            appId ?: ""
        } catch (e: Exception) {
            ""
        }
}

/**
 * Factory function to create AndroidOtelPlatformProvider with full service dependencies.
 * This is a convenience function that creates the provider with service getters.
 */
internal fun createAndroidOtelPlatformProvider(
    applicationService: com.onesignal.core.internal.application.IApplicationService,
    installIdService: com.onesignal.core.internal.device.IInstallIdService,
    configModelStore: com.onesignal.core.internal.config.ConfigModelStore,
    identityModelStore: com.onesignal.user.internal.identity.IdentityModelStore,
): AndroidOtelPlatformProvider {
    val context = applicationService.appContext
    val crashStoragePath = context.cacheDir.path + java.io.File.separator +
        "onesignal" + java.io.File.separator +
        "otel" + java.io.File.separator +
        "crashes"

    return AndroidOtelPlatformProvider(
        AndroidOtelPlatformProviderConfig(
            crashStoragePath = crashStoragePath,
            appPackageId = context.packageName,
            appVersion = com.onesignal.common.AndroidUtils.getAppVersion(context) ?: "unknown",
            getInstallIdProvider = { installIdService.getId().toString() },
            context = context,
            getAppId = {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    configModelStore.model.appId
                } catch (e: Exception) {
                    null
                }
            },
            getOnesignalId = {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    val onesignalId = identityModelStore.model.onesignalId
                    onesignalId.takeIf { !IDManager.isLocalId(it) }
                } catch (e: Exception) {
                    null
                }
            },
            getPushSubscriptionId = {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    val pushSubscriptionId = configModelStore.model.pushSubscriptionId
                    pushSubscriptionId?.takeIf { !IDManager.isLocalId(pushSubscriptionId) }
                } catch (e: Exception) {
                    null
                }
            },
            getIsInForeground = { applicationService.isInForeground },
            getRemoteLogLevel = {
                @Suppress("TooGenericExceptionCaught", "SwallowedException")
                try {
                    configModelStore.model.remoteLoggingParams.logLevel
                } catch (e: Exception) {
                    null
                }
            },
        )
    )
}
