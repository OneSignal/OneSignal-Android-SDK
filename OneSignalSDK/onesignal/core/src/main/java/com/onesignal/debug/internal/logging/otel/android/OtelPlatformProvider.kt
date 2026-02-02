package com.onesignal.debug.internal.logging.otel.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelPlatformProvider

/**
 * Configuration for AndroidOtelPlatformProvider.
 */
internal data class OtelPlatformProviderConfig(
    val crashStoragePath: String,
    val appPackageId: String,
    val appVersion: String,
    val context: Context? = null,
    val getIsInForeground: (() -> Boolean?)? = null,
)

/**
 * Android-specific implementation of IOtelPlatformProvider.
 * Reads all values directly from SharedPreferences and system services.
 * No SDK service dependencies required.
 *
 * All IDs (appId, onesignalId, pushSubscriptionId) are resolved from SharedPreferences via OtelIdResolver.
 * Remote log level defaults to ERROR if not found in config.
 */
internal class OtelPlatformProvider(
    config: OtelPlatformProviderConfig,
) : IOtelPlatformProvider {
    override val appPackageId: String = config.appPackageId
    override val appVersion: String = config.appVersion
    private val context: Context? = config.context
    private val getIsInForeground: (() -> Boolean?)? = config.getIsInForeground
    private val idResolver = OtelIdResolver(context)

    // Top-level attributes (static, calculated once)
    override suspend fun getInstallId(): String = idResolver.resolveInstallId()

    override val sdkBase: String = "android"

    override val sdkBaseVersion: String = OneSignalUtils.sdkVersion

    override val deviceManufacturer: String = Build.MANUFACTURER

    override val deviceModel: String = Build.MODEL

    override val osName: String = "Android"

    override val osVersion: String = Build.VERSION.RELEASE

    override val osBuildId: String = Build.ID

    override val sdkWrapper: String? = OneSignalWrapper.sdkType

    override val sdkWrapperVersion: String? = OneSignalWrapper.sdkVersion

    // Per-event attributes - IDs are cached (calculated once), appState is dynamic (calculated per access)
    override val appId: String? by lazy {
        idResolver.resolveAppId()
    }

    override val onesignalId: String? by lazy {
        idResolver.resolveOnesignalId()
    }

    override val pushSubscriptionId: String? by lazy {
        idResolver.resolvePushSubscriptionId()
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
    override val processUptime: Long
        get() = SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()

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
     * - If remote config is null or unavailable: default to "ERROR" (always log errors)
     * - If remote config is explicitly NONE: return "NONE" (no logs including errors)
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val remoteLogLevel: String? by lazy {
        try {
            val configLevel = idResolver.resolveRemoteLogLevel()
            when {
                // Remote config is populated and working well - use whatever is sent from there
                configLevel != null && configLevel != com.onesignal.debug.LogLevel.NONE -> configLevel.name
                // Explicitly NONE means no logging (including errors)
                configLevel == com.onesignal.debug.LogLevel.NONE -> "NONE"
                // Remote config not available - default to ERROR (always log errors)
                else -> "ERROR"
            }
        } catch (e: Exception) {
            // If there's an error accessing config, default to ERROR (always log errors)
            // Exception is intentionally swallowed to avoid recursion in logging
            "ERROR"
        }
    }

    override val appIdForHeaders: String
        get() = appId ?: ""
}

/**
 * Factory function to create AndroidOtelPlatformProvider without service dependencies.
 * Reads all values directly from SharedPreferences and system services.
 */
internal fun createAndroidOtelPlatformProvider(
    context: Context,
): OtelPlatformProvider {
    val crashStoragePath = context.cacheDir.path + java.io.File.separator +
        "onesignal" + java.io.File.separator +
        "otel" + java.io.File.separator +
        "crashes"

    return OtelPlatformProvider(
        OtelPlatformProviderConfig(
            crashStoragePath = crashStoragePath,
            appPackageId = context.packageName,
            appVersion = com.onesignal.common.AndroidUtils.getAppVersion(context) ?: "unknown",
            context = context,
        )
    )
}
