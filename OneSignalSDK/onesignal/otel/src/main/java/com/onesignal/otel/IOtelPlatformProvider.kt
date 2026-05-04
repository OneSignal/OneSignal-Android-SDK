package com.onesignal.otel

/**
 * Platform-agnostic provider interface for injecting platform-specific values.
 * All Android/iOS specific values should be provided through this interface.
 */
interface IOtelPlatformProvider {
    // Top-level attributes (static, calculated once)
    /**
     * Gets the installation ID for this device.
     * This is an async operation as it may need to generate a new ID if one doesn't exist.
     *
     * @return The installation ID as a string
     */
    suspend fun getInstallId(): String
    val sdkBase: String
    val sdkBaseVersion: String
    val appPackageId: String
    val appVersion: String
    val deviceManufacturer: String
    val deviceModel: String
    val osName: String
    val osVersion: String
    val osBuildId: String
    val sdkWrapper: String?
    val sdkWrapperVersion: String?

    /**
     * The canonical keys of feature flags currently enabled for this device, as resolved by
     * the platform's feature flag source (on Android, a constructor-injected `IFeatureManager`).
     * Read fresh on every access so per-event OTel attributes always reflect the current state.
     *
     * Empty when no flags are enabled or the platform source returns/throws nothing usable.
     * Defaults to an empty list so existing platform implementations remain source/binary
     * compatible — platforms that want to populate `ossdk.feature_flags` should override.
     *
     * The order is not guaranteed; consumers that need a deterministic encoding (e.g. for
     * stable log payloads) should sort before serializing.
     */
    val enabledFeatureFlags: List<String>
        get() = emptyList()

    // Per-event attributes (dynamic, calculated per event)
    val appId: String?
    val onesignalId: String?
    val pushSubscriptionId: String?
    val appState: String // "foreground" or "background"
    val processUptime: Long // in milliseconds
    val currentThreadName: String

    // Crash-specific configuration
    val crashStoragePath: String
    val minFileAgeForReadMillis: Long

    // Remote logging configuration
    /**
     * Whether remote logging (crash reporting, ANR detection, remote log shipping) is enabled.
     * Derived from the presence of a valid log_level in logging_config:
     * - "logging_config": {} → false (not on allowlist)
     * - "logging_config": {"log_level": "ERROR"} → true (on allowlist)
     * Defaults to false on first launch (before remote config is cached).
     */
    val isRemoteLoggingEnabled: Boolean

    /**
     * The minimum log level to send remotely as a string (e.g., "ERROR", "WARN").
     * Null when logging_config is empty or not yet cached (disabled).
     * Valid values: "NONE", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "VERBOSE"
     */
    val remoteLogLevel: String?

    /**
     * Debug-only toggle for local exporter diagnostics.
     * When true, Otel exporter request/response success/failure logs are emitted to logcat.
     */
    val isOtelExporterLoggingEnabled: Boolean

    val appIdForHeaders: String

    /**
     * Base URL for the OneSignal API (e.g. "https://api.onesignal.com").
     * The Otel exporter appends "sdk/otel/v1/logs" to this.
     * Sourced from the core module so all SDK traffic hits the same host.
     */
    val apiBaseUrl: String
}
