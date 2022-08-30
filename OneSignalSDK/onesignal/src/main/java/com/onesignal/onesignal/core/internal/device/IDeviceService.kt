package com.onesignal.onesignal.core.internal.device

/**
 * Provides access to the underlying device information.
 */
interface IDeviceService {
    val isGooglePlayServicesAvailable: Boolean
    val isGooglePlayStoreInstalled: Boolean
    val isHMSAvailable: Boolean
    val isAndroidDeviceType: Boolean
    val isFireOSDeviceType: Boolean
    val isHuaweiDeviceType: Boolean
    val deviceType: Int
    val isGMSInstalledAndEnabled: Boolean
    val hasAllHMSLibrariesForPushKit: Boolean
    val hasFCMLibrary: Boolean
}