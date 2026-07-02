package com.onesignal.logger

/**
 * Platform-agnostic provider for all platform-specific values the logger pipeline
 * needs. This is the direct analogue of `IOtelPlatformProvider`.
 *
 * Everything the module cannot compute in pure common code — device/app metadata,
 * IDs, config, storage location — is injected here. Implementations live in the
 * platform layer (Android: core module; iOS: Swift-backed actual later).
 */
interface ILoggerPlatformProvider {
    // ---- Top-level / resource attributes (static, calculated once) ----

    /**
     * Installation ID for this device. Suspending because it may need to generate
     * and persist a new ID on first access.
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
     * Canonical keys of feature flags currently enabled for this device. Read
     * fresh on every access so per-event attributes reflect the current state.
     * Defaults to empty for source/binary compatibility.
     */
    val enabledFeatureFlags: List<String>
        get() = emptyList()

    // ---- Per-event attributes (dynamic, calculated per event) ----

    val appId: String?
    val onesignalId: String?
    val pushSubscriptionId: String?

    /** "foreground", "background", or "unknown". */
    val appState: String

    /** Process uptime in milliseconds. */
    val processUptime: Long

    val currentThreadName: String

    // ---- Crash-specific configuration ----

    val crashStoragePath: String
    val minFileAgeForReadMillis: Long

    // ---- Remote logging configuration ----

    /**
     * Whether remote logging (crash reporting, ANR detection, remote log shipping)
     * is enabled. Defaults to false on first launch (before remote config cached).
     */
    val isRemoteLoggingEnabled: Boolean

    /**
     * Minimum log level to send remotely (e.g. "ERROR", "WARN"). Null when remote
     * logging config is empty / not yet cached.
     */
    val remoteLogLevel: String?

    /** Debug-only toggle for local exporter diagnostics. */
    val isExporterLoggingEnabled: Boolean

    /** App id used for the `app_id` query param / headers. */
    val appIdForHeaders: String

    /**
     * Base URL for the OneSignal API (e.g. "https://api.onesignal.com").
     * The log endpoint path is appended to this.
     */
    val apiBaseUrl: String
}
