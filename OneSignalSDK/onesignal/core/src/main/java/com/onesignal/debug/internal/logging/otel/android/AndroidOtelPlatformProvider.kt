package com.onesignal.debug.internal.logging.otel.android

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.user.internal.identity.IdentityModelStore
import java.io.File

/**
 * Android-specific implementation of IOtelPlatformProvider.
 * This injects all Android-specific values into the platform-agnostic otel module.
 */
internal class AndroidOtelPlatformProvider(
    private val applicationService: IApplicationService,
    private val installIdService: IInstallIdService,
    private val configModelStore: ConfigModelStore,
    private val identityModelStore: IdentityModelStore,
    private val time: ITime,
) : IOtelPlatformProvider {
    // Top-level attributes (static, calculated once)
    override suspend fun getInstallId(): String =
        installIdService.getId().toString()

    override val sdkBase: String = "android"

    override val sdkBaseVersion: String = OneSignalUtils.sdkVersion

    override val appPackageId: String
        get() = applicationService.appContext.packageName

    override val appVersion: String
        get() = AndroidUtils.getAppVersion(applicationService.appContext) ?: "unknown"

    override val deviceManufacturer: String = Build.MANUFACTURER

    override val deviceModel: String = Build.MODEL

    override val osName: String = "Android"

    override val osVersion: String = Build.VERSION.RELEASE

    override val osBuildId: String = Build.ID

    override val sdkWrapper: String? = OneSignalWrapper.sdkType

    override val sdkWrapperVersion: String? = OneSignalWrapper.sdkVersion

    // Per-event attributes (dynamic, calculated per event)
    override val appId: String?
        get() = try {
            configModelStore.model.appId
        } catch (_: NullPointerException) {
            Logging.warn("app_id not available to add to crash log")
            null
        }

    override val onesignalId: String?
        get() = try {
            val onesignalId = identityModelStore.model.onesignalId
            if (com.onesignal.common.IDManager.isLocalId(onesignalId)) {
                null
            } else {
                onesignalId
            }
        } catch (_: NullPointerException) {
            Logging.warn("onesignalId not available to add to crash log")
            null
        }

    override val pushSubscriptionId: String?
        get() = try {
            val pushSubscriptionId = configModelStore.model.pushSubscriptionId
            if (pushSubscriptionId == null ||
                com.onesignal.common.IDManager.isLocalId(pushSubscriptionId)
            ) {
                null
            } else {
                pushSubscriptionId
            }
        } catch (_: NullPointerException) {
            Logging.warn("subscriptionId not available to add to crash log")
            null
        }

    // https://opentelemetry.io/docs/specs/semconv/registry/attributes/android/
    override val appState: String
        get() = if (applicationService.isInForeground) "foreground" else "background"

    // https://opentelemetry.io/docs/specs/semconv/system/process-metrics/#metric-processuptime
    override val processUptime: Double
        get() = time.processUptimeMillis / 1_000.0

    // https://opentelemetry.io/docs/specs/semconv/general/attributes/#general-thread-attributes
    override val currentThreadName: String
        get() = Thread.currentThread().name

    // Crash-specific configuration
    override val crashStoragePath: String
        get() {
            val path = applicationService.appContext.cacheDir.path + File.separator +
                "onesignal" + File.separator +
                "otel" + File.separator +
                "crashes"
            // Log the path on first access so developers know where to find crash logs
            Logging.info("OneSignal: Crash logs stored at: $path")
            return path
        }

    override val minFileAgeForReadMillis: Long = 5_000

    // Remote logging configuration
    override val remoteLoggingEnabled: Boolean
        get() = try {
            configModelStore.model.remoteLoggingParams.enable ?: true
        } catch (_: NullPointerException) {
            false
        }

    override val appIdForHeaders: String
        get() = try {
            configModelStore.model.appId
        } catch (_: NullPointerException) {
            Logging.error("Auth missing for crash log reporting!")
            ""
        }
}
