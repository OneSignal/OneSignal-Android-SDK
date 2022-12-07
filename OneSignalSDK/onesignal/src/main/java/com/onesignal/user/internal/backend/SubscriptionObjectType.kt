package com.onesignal.user.internal.backend

import com.onesignal.core.internal.device.IDeviceService

enum class SubscriptionObjectType(val value: String) {
    IOS_PUSH("iOSPush"),
    ANDROID_PUSH("AndroidPush"),
    FIREOS_PUSH("FireOSPush"),
    CHROME_EXTENSION("ChromeExtensionPush"),
    CHROME_PUSH("ChromePush"),
    WINDOWS_PUSH("WindowsPush"),
    SAFARI_PUSH("SafariPush"),
    SAFARI_PUSH_LEGACY("SafariLegacyPush"),
    FIREFOX_PUSH("FirefoxPush"),
    MACOS_PUSH("macOSPush"),
    EMAIL("Email"),
    HUAWEI_PUSH("HuaweiPush"),
    SMS("SMS");

    companion object {
        fun fromDeviceType(type: IDeviceService.DeviceType): SubscriptionObjectType {
            return when (type) {
                IDeviceService.DeviceType.Android -> ANDROID_PUSH
                IDeviceService.DeviceType.Fire -> FIREOS_PUSH
                IDeviceService.DeviceType.Huawei -> HUAWEI_PUSH
            }
        }

        fun fromString(type: String): SubscriptionObjectType? = values().firstOrNull() { it.value.equals(type, true) }
    }
}
