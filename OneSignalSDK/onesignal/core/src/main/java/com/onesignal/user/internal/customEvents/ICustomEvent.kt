package com.onesignal.user.internal.customEvents

interface ICustomEvent {
    val name: String
    val properties: Map<String, Any>?
}
