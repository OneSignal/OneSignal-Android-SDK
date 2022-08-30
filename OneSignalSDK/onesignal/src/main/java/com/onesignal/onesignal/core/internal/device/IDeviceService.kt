package com.onesignal.onesignal.core.internal.device

interface IDeviceService {
    val isGooglePlayServicesAvailable: Boolean
    val isGooglePlayStoreInstalled: Boolean
    val isHMSAvailable: Boolean
    val isAndroidDeviceType: Boolean
    val isFireOSDeviceType: Boolean
    val isHuaweiDeviceType: Boolean

    val deviceType: Int

    fun isGMSInstalledAndEnabled(): Boolean
    fun hasAllHMSLibrariesForPushKit(): Boolean
    fun hasFCMLibrary(): Boolean
}
