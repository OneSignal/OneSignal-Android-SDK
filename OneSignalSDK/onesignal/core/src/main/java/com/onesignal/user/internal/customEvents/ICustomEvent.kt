package com.onesignal.user.internal.customEvents

interface ICustomEvent {
    val name: String
    val properties: Map<String, Any>?
    val onesignalId: String?
    val externalId: String?
    val timeStamp: Long
    val deviceType: String
    val sdk: String
    val appVersion: String
}
