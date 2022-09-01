package com.onesignal.notification.internal.registration.impl

internal interface IPushRegistratorCallback {
    suspend fun fireCallback(id: String?)
}
