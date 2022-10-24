package com.onesignal.core.internal.device

interface IDeviceService {
    val isGooglePlayStoreInstalled: Boolean
    val isAndroidDeviceType: Boolean
    val isFireOSDeviceType: Boolean
    val isHuaweiDeviceType: Boolean

    val deviceType: Int

    val isGMSInstalledAndEnabled: Boolean
    val hasAllHMSLibrariesForPushKit: Boolean
    val hasFCMLibrary: Boolean
    val androidSupportLibraryStatus: AndroidSupportLibraryStatus

    enum class AndroidSupportLibraryStatus {
        MISSING,
        OUTDATED,
        OK
    }
}
