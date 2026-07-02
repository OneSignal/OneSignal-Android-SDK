package com.onesignal.debug.internal.logging.logger.android

import com.onesignal.logger.ILoggerPlatformProvider
import com.onesignal.otel.IOtelPlatformProvider

/**
 * Adapts the existing Android [IOtelPlatformProvider] to the `logger` module's
 * [ILoggerPlatformProvider]. This reuses all of the battle-tested Android value
 * resolution (IDs, config, device metadata) so the logger pipeline reads exactly the
 * same values as the otel pipeline — only the consuming interface differs.
 *
 * When `otel` is eventually removed, the underlying provider's logic can move into a
 * native [ILoggerPlatformProvider] implementation and this adapter deleted.
 */
internal class LoggerPlatformProviderAdapter(
    private val delegate: IOtelPlatformProvider,
) : ILoggerPlatformProvider {
    override suspend fun getInstallId(): String = delegate.getInstallId()

    override val sdkBase: String get() = delegate.sdkBase
    override val sdkBaseVersion: String get() = delegate.sdkBaseVersion
    override val appPackageId: String get() = delegate.appPackageId
    override val appVersion: String get() = delegate.appVersion
    override val deviceManufacturer: String get() = delegate.deviceManufacturer
    override val deviceModel: String get() = delegate.deviceModel
    override val osName: String get() = delegate.osName
    override val osVersion: String get() = delegate.osVersion
    override val osBuildId: String get() = delegate.osBuildId
    override val sdkWrapper: String? get() = delegate.sdkWrapper
    override val sdkWrapperVersion: String? get() = delegate.sdkWrapperVersion
    override val enabledFeatureFlags: List<String> get() = delegate.enabledFeatureFlags

    override val appId: String? get() = delegate.appId
    override val onesignalId: String? get() = delegate.onesignalId
    override val pushSubscriptionId: String? get() = delegate.pushSubscriptionId
    override val appState: String get() = delegate.appState
    override val processUptime: Long get() = delegate.processUptime
    override val currentThreadName: String get() = delegate.currentThreadName

    override val crashStoragePath: String get() = delegate.crashStoragePath
    override val minFileAgeForReadMillis: Long get() = delegate.minFileAgeForReadMillis

    override val isRemoteLoggingEnabled: Boolean get() = delegate.isRemoteLoggingEnabled
    override val remoteLogLevel: String? get() = delegate.remoteLogLevel
    override val isExporterLoggingEnabled: Boolean get() = delegate.isOtelExporterLoggingEnabled
    override val appIdForHeaders: String get() = delegate.appIdForHeaders
    override val apiBaseUrl: String get() = delegate.apiBaseUrl
}
