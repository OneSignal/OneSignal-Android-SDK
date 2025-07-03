package com.onesignal.user.internal.customEvents

import com.onesignal.user.internal.customEvents.impl.CustomEventProperty

interface ICustomEvent {
    val name: String
    val onesignalId: String
    val externalId: String?
    val timeStamp: String
    val properties: Map<String, CustomEventProperty>?
}