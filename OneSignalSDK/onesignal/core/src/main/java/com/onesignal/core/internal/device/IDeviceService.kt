package com.onesignal.core.internal.device

interface IDeviceService {
    val isAndroidDeviceType: Boolean
    val isFireOSDeviceType: Boolean
    val isHuaweiDeviceType: Boolean

    val deviceType: DeviceType

    val isGMSInstalledAndEnabled: Boolean
    val hasAllHMSLibrariesForPushKit: Boolean
    val hasFCMLibrary: Boolean
    val jetpackLibraryStatus: JetpackLibraryStatus
    val supportsHMS: Boolean

    fun supportsGooglePush(): Boolean

    enum class JetpackLibraryStatus {
        MISSING,
        OUTDATED,
        OK,
    }

    enum class DeviceType(
        val value: Int,
    ) {
        Fire(2),
        Android(1),
        Huawei(13),
    }
}
