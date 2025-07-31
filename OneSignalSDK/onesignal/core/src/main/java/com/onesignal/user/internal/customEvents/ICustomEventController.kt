package com.onesignal.user.internal.customEvents

interface ICustomEventController {
    fun sendCustomEvent(
        name: String,
        properties: Map<String, Any>?,
    )
}
