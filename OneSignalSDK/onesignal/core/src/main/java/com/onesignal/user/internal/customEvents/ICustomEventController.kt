package com.onesignal.user.internal.customEvents

import com.onesignal.user.internal.customEvents.impl.CustomEvent

interface ICustomEventController {
    fun sendCustomEvent(event: CustomEvent)
}
