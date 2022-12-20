package com.onesignal.core.internal.device

interface IDeviceService {
    val isAndroidDeviceType: Boolean
    val isFireOSDeviceType: Boolean
    val isHuaweiDeviceType: Boolean

    val deviceType: DeviceType

    val isGMSInstalledAndEnabled: Boolean
    val hasAllHMSLibrariesForPushKit: Boolean
    val hasFCMLibrary: Boolean
    val androidSupportLibraryStatus: AndroidSupportLibraryStatus

    enum class AndroidSupportLibraryStatus {
        MISSING,
        OUTDATED,
        OK
    }

    enum class DeviceType(val value: Int) {
        Fire(2),
        Android(1),
        Huawei(13)
    }
}
