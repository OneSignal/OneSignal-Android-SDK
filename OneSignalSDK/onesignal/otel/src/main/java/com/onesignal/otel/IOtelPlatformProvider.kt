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

    // Per-event attributes (dynamic, calculated per event)
    val appId: String?
    val onesignalId: String?
    val pushSubscriptionId: String?
    val appState: String // "foreground" or "background"
    val processUptime: Long // in ms
    val currentThreadName: String

    // Crash-specific configuration
    val crashStoragePath: String
    val minFileAgeForReadMillis: Long

    // Remote logging configuration
    /**
     * The minimum log level to send remotely as a string (e.g., "ERROR", "WARN", "NONE").
     * If null, defaults to ERROR level for client-side logging.
     * If "NONE", no logs (including errors) will be sent remotely.
     * Valid values: "NONE", "FATAL", "ERROR", "WARN", "INFO", "DEBUG", "VERBOSE"
     */
    val remoteLogLevel: String?
    val appIdForHeaders: String
}
