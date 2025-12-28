package com.onesignal.otel

/**
 * Platform-agnostic provider interface for injecting platform-specific values.
 * All Android/iOS specific values should be provided through this interface.
 */
interface IOtelPlatformProvider {
    // Top-level attributes (static, calculated once)
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
    val processUptime: Double // in seconds
    val currentThreadName: String

    // Crash-specific configuration
    val crashStoragePath: String
    val minFileAgeForReadMillis: Long

    // Remote logging configuration
    val remoteLoggingEnabled: Boolean
    val appIdForHeaders: String
}
