package com.onesignal.debug.internal.logging.otel.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.http.OneSignalService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelPlatformProvider
import java.io.File

// Use this to enable/disable the Otel exporter logging in debug builds.
internal const val OTEL_EXPORTER_LOGGING_ENABLED = false

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
    private val featureManagerProvider: () -> IFeatureManager,
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

    // Host language version for remote-log dashboard filtering (ossdk.kotlin_version).
    // KotlinVersion.CURRENT reflects the stdlib the SDK was compiled against.
    override val kotlinVersion: String? = KotlinVersion.CURRENT.toString()

    // Extra toolchain labels under ossdk.* for dashboard filtering. Prefer values that are
    // stable for the process lifetime and cheap to read:
    // - java_version: ART's reported Java language level (spec version, e.g. "17")
    // - android_api_level: device API level (SDK_INT) — complementary to os.version (RELEASE)
    // Build-time-only values (AGP, compileSdk, NDK) are not available here at runtime.
    override val additionalVersionAttributes: Map<String, String> =
        buildMap {
            System.getProperty("java.specification.version")
                ?.takeIf { it.isNotBlank() }
                ?.let { put("java_version", it) }
            put("android_api_level", Build.VERSION.SDK_INT.toString())
        }

    // Read through the supplier on every access so per-event attributes always reflect the
    // current featureStates snapshot (including IMMEDIATE-mode flag changes). The supplier is
    // an immutable constructor val that resolves IFeatureManager lazily — this lets the OTel
    // pipeline come up early in init (before service bootstrap) without mutable late-bound
    // state. Returns an empty list when the supplier or the manager throws (e.g. very early
    // emissions before services are ready); the attribute is then omitted by OtelFieldsPerEvent.
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
    // Mid-session config updates are handled by OtelLifecycleManager reading
    // from ConfigModel directly, not from these cached values.
    override val isRemoteLoggingEnabled: Boolean by lazy {
        idResolver.resolveRemoteLoggingEnabled()
    }

    // Cached from SharedPreferences on first access and held for the session.
    // Mid-session config updates are handled by OtelLifecycleManager reading
    // from ConfigModel directly, not from these cached values.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val remoteLogLevel: String? by lazy {
        try {
            idResolver.resolveRemoteLogLevel()?.name
        } catch (e: Exception) {
            null
        }
    }

    override val isOtelExporterLoggingEnabled: Boolean = OTEL_EXPORTER_LOGGING_ENABLED

    override val appIdForHeaders: String
        get() = appId ?: ""

    override val apiBaseUrl: String = OneSignalService.ONESIGNAL_API_BASE_URL
}

/**
 * Factory function to create AndroidOtelPlatformProvider. Reads value-config directly from
 * SharedPreferences / system services; receives a [featureManagerProvider] supplier that the
 * provider invokes lazily on each `enabledFeatureFlags` read so the OTel pipeline can come up
 * before service bootstrap completes.
 */
internal fun createAndroidOtelPlatformProvider(
    context: Context,
    featureManagerProvider: () -> IFeatureManager,
): OtelPlatformProvider {
    return OtelPlatformProvider(
        OtelPlatformProviderConfig(
            crashStoragePath = getOtelCrashStoragePath(context),
            appPackageId = context.packageName,
            appVersion = com.onesignal.common.AndroidUtils.getAppVersion(context) ?: "unknown",
            context = context,
        ),
        featureManagerProvider = featureManagerProvider,
    )
}

internal fun getOtelCrashStoragePath(context: Context): String =
    File(File(File(context.cacheDir, "onesignal"), "otel"), "crashes").path
