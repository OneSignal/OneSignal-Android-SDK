package com.onesignal.notification.internal.registration.impl

interface IPushRegistratorCallback {
    suspend fun fireCallback(id: String?)
}
