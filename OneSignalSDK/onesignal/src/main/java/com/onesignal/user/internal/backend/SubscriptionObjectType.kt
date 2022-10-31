package com.onesignal.user.internal.backend

enum class SubscriptionObjectType(val value: String) {
    IOS_PUSH("iOS"),
    ANDROID_PUSH("Android"),
    FIREOS_PUSH("FireOS"),
    CHROME_EXTENSION("ChromeExtension"),
    CHROME_PUSH("ChromePush"),
    WINDOWS_PUSH("WindowsPush"),
    SAFARI_PUSH("SafariPush"),
    SAFARI_PUSH_LEGACY("SafariLegacyPush"),
    FIREFOX_PUSH("FirefoxPush"),
    MACOS_PUSH("macOSPush"),
    ALEXA_PUSH("AlexaPush"),
    EMAIL("Email"),
    HUAWEI_PUSH("HuaweiPush"),
    SMS("SMS");

    companion object {
        fun fromDeviceType(type: Int): SubscriptionObjectType {
            return when (type) {
                0 -> IOS_PUSH
                1 -> ANDROID_PUSH
                2 -> FIREOS_PUSH
                4 -> CHROME_EXTENSION
                5 -> CHROME_PUSH
                6 -> WINDOWS_PUSH
                7 -> SAFARI_PUSH_LEGACY
                8 -> FIREFOX_PUSH
                9 -> MACOS_PUSH
                10 -> ALEXA_PUSH
                11 -> EMAIL
                13 -> HUAWEI_PUSH
                14 -> SMS
                16 -> SAFARI_PUSH
                else -> ANDROID_PUSH
            }
        }
    }
}
