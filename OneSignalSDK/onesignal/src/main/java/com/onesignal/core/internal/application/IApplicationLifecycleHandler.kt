package com.onesignal.core.internal.application

internal interface IApplicationLifecycleHandler {
    fun onFocus()
    fun onUnfocused()
}
