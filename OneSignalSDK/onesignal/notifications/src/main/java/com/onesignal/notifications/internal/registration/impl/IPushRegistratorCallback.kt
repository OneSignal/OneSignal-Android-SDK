package com.onesignal.notifications.internal.registration.impl

internal interface IPushRegistratorCallback {
    suspend fun fireCallback(id: String?)
}
