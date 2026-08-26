package com.onesignal.debug.internal.logging.logger.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.http.OneSignalService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILoggerPlatformProvider
import java.io.File

// Use this to enable/disable local exporter diagnostics in debug builds.
internal const val EXPORTER_LOGGING_ENABLED = false

/**
 * Configuration for [LoggerPlatformProvider].
 */
internal data class LoggerPlatformProviderConfig(
    val crashStoragePath: String,
    val appPackageId: String,
    val appVersion: String,
    val context: Context? = null,
    val getIsInForeground: (() -> Boolean?)? = null,
)

/**
 * Android implementation of [ILoggerPlatformProvider].
 * Reads all values directly from SharedPreferences and system services.
 * No SDK service dependencies required.
 *
 * All IDs (appId, onesignalId, pushSubscriptionId) are resolved from SharedPreferences via
 * [LoggerIdResolver]. Remote log level defaults to ERROR if not found in config.
 */
internal class LoggerPlatformProvider(
    config: LoggerPlatformProviderConfig,
    private val featureManagerProvider: () -> IFeatureManager,
) : ILoggerPlatformProvider {
    override val appPackageId: String = config.appPackageId
    override val appVersion: String = config.appVersion
    private val context: Context? = config.context
    private val getIsInForeground: (() -> Boolean?)? = config.getIsInForeground
    private val idResolver = LoggerIdResolver(context)

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

    // Compile-time Kotlin stdlib, not the host app's Kotlin.
    override val kotlinVersion: String? = KotlinVersion.CURRENT.toString()

    override val swiftVersion: String? = null

    // Device API level; complementary to os.version (RELEASE).
    // Do not emit java_version — ART hardcodes java.specification.version to "0.9".
    override val additionalVersionAttributes: Map<String, String> =
        mapOf("android_api_level" to Build.VERSION.SDK_INT.toString())

    // Read through the supplier on every access so per-event attributes always reflect the
    // current featureStates snapshot (including IMMEDIATE-mode flag changes). The supplier is
    // an immutable constructor val that resolves IFeatureManager lazily — this lets the logging
    // pipeline come up early in init (before service bootstrap) without mutable late-bound
    // state. Returns an empty list when the supplier or the manager throws (e.g. very early
    // emissions before services are ready); the attribute is then omitted downstream.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val enabledFeatureFlags: List<String>
        get() = try {
            featureManagerProvider().enabledFeatureKeys()
        } catch (t: Throwable) {
            emptyList()
        }

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
        get() = android.os.SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis()

    // https://opentelemetry.io/docs/specs/semconv/general/attributes/#general-thread-attributes
    override val currentThreadName: String
        get() = Thread.currentThread().name

    override val crashStoragePath: String by lazy {
        val path = config.crashStoragePath
        Logging.info("OneSignal: Crash logs stored at: $path")
        path
    }

    override val minFileAgeForReadMillis: Long = 5_000

    // Cached from SharedPreferences on first access and held for the session.
    // Mid-session config updates are handled by LoggerLifecycleManager reading
    // from ConfigModel directly, not from these cached values.
    override val isRemoteLoggingEnabled: Boolean by lazy {
        idResolver.resolveRemoteLoggingEnabled()
    }

    // Cached from SharedPreferences on first access and held for the session.
    // Mid-session config updates are handled by LoggerLifecycleManager reading
    // from ConfigModel directly, not from these cached values.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val remoteLogLevel: String? by lazy {
        try {
            idResolver.resolveRemoteLogLevel()?.name
        } catch (e: Exception) {
            null
        }
    }

    override val isExporterLoggingEnabled: Boolean = EXPORTER_LOGGING_ENABLED

    override val appIdForHeaders: String
        get() = appId ?: ""

    override val apiBaseUrl: String = OneSignalService.ONESIGNAL_API_BASE_URL
}

/**
 * Factory function to create the Android [ILoggerPlatformProvider]. Reads value-config directly
 * from SharedPreferences / system services; receives a [featureManagerProvider] supplier that the
 * provider invokes lazily on each `enabledFeatureFlags` read so the logging pipeline can come up
 * before service bootstrap completes.
 */
internal fun createAndroidLoggerPlatformProvider(
    context: Context,
    featureManagerProvider: () -> IFeatureManager,
): LoggerPlatformProvider {
    return LoggerPlatformProvider(
        LoggerPlatformProviderConfig(
            crashStoragePath = getCrashStoragePath(context),
            appPackageId = context.packageName,
            appVersion = com.onesignal.common.AndroidUtils.getAppVersion(context) ?: "unknown",
            context = context,
        ),
        featureManagerProvider = featureManagerProvider,
    )
}

/**
 * The `otel` path segment is kept even though OpenTelemetry is gone: it is the directory
 * upgrading installs already hold crash records in, and moving it would orphan pending
 * uploads. Legacy OTel-format records left behind are reclaimed by
 * [com.onesignal.logger.crash.CrashRetention.selectUnrecognized].
 */
internal fun getCrashStoragePath(context: Context): String =
    File(File(File(context.cacheDir, "onesignal"), "otel"), "crashes").path
