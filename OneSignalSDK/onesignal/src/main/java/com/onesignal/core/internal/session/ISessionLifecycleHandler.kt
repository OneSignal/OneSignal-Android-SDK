package com.onesignal.core.internal.session

internal interface ISessionLifecycleHandler {
    fun sessionStarted()
    fun sessionResumed()
}
