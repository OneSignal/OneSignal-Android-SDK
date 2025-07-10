package com.onesignal.user.internal.customEvents

interface ICustomEvent {
    val appId: String
    val name: String
    val properties: Map<String, Any>?
    val onesignalId: String?
    val externalId: String?
    val timeStamp: String
    val deviceType: String
    val sdk: String
    val appVersion: String
}
