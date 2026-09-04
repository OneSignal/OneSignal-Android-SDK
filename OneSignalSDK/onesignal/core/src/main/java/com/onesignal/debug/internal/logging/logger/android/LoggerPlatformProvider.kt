package com.onesignal.debug.internal.logging.logger.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.http.OneSignalService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.logger.ILoggerPlatformProvider
import java.io.File

// Use this to enable/disable local exporter diagnostics in debug builds.
internal const val EXPORTER_LOGGING_ENABLED = false

/** Configuration for [LoggerPlatformProvider]. */
internal data class LoggerPlatformProviderConfig(
    val crashStoragePath: String,
    val appPackageId: String,
    val appVersion: String,
    val context: Context? = null,
    val getIsInForeground: (() -> Boolean?)? = null,
)

/**
 * Android [ILoggerPlatformProvider], reading straight from SharedPreferences and system services
 * so the logging pipeline can come up before SDK service bootstrap.
 */
internal class LoggerPlatformProvider(
    config: LoggerPlatformProviderConfig,
    private val featureManagerProvider: () -> IFeatureManager,
) : ILoggerPlatformProvider {
    companion object {
        // First class load. Same monotonic clock as Process.getStartUptimeMillis() (API 24).
        private val processStartUptimeMs = SystemClock.uptimeMillis()
    }
    override val appPackageId: String = config.appPackageId
    override val appVersion: String = config.appVersion
    private val context: Context? = config.context
    private val getIsInForeground: (() -> Boolean?)? = config.getIsInForeground
    private val idResolver = LoggerIdResolver(context)

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

    // Do not add java_version here — ART hardcodes java.specification.version to "0.9".
    override val additionalVersionAttributes: Map<String, String> =
        mapOf("android_api_level" to Build.VERSION.SDK_INT.toString())

    // Resolve through the supplier on every access, so per-event attributes track the current
    // featureStates snapshot. Empty when it throws, which it does before services are ready.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val enabledFeatureFlags: List<String>
        get() = try {
            featureManagerProvider().enabledFeatureKeys()
        } catch (t: Throwable) {
            emptyList()
        }

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
            getIsInForeground?.invoke()?.let { isForeground ->
                if (isForeground) "foreground" else "background"
            } ?: run {
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

    override val processUptime: Long
        get() = SystemClock.uptimeMillis() - processStartUptimeMs

    // https://opentelemetry.io/docs/specs/semconv/general/attributes/#general-thread-attributes
    override val currentThreadName: String
        get() = Thread.currentThread().name

    override val crashStoragePath: String by lazy {
        val path = config.crashStoragePath
        Logging.info("OneSignal: Crash logs stored at: $path")
        path
    }

    override val minFileAgeForReadMillis: Long = 5_000

    // Session-scoped: mid-session updates reach LoggerLifecycleManager via ConfigModel, not here.
    override val isRemoteLoggingEnabled: Boolean by lazy {
        idResolver.resolveRemoteLoggingEnabled()
    }

    // Session-scoped, as above.
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
 * Builds the Android [ILoggerPlatformProvider]. [featureManagerProvider] is resolved lazily per
 * read, so the logging pipeline can come up before service bootstrap completes.
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
 * The `otel` segment stays although OpenTelemetry is gone: upgrading installs already hold crash
 * records there, and moving it would orphan pending uploads.
 */
internal fun getCrashStoragePath(context: Context): String =
    File(File(File(context.cacheDir, "onesignal"), "otel"), "crashes").path
