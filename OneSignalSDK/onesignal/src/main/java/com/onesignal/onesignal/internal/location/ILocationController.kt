package com.onesignal.onesignal.internal.location

interface ILocationController {
    fun start()
    fun onFocusChange()
    fun stop()
}