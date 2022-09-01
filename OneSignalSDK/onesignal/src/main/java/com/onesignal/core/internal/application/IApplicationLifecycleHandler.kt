package com.onesignal.core.internal.application

interface IApplicationLifecycleHandler {
    fun onFocus()
    fun onUnfocused()
}
