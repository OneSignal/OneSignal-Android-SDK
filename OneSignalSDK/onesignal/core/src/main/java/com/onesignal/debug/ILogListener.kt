package com.onesignal.debug

fun interface ILogListener {
    fun onLogEvent(event: OneSignalLogEvent)
}
