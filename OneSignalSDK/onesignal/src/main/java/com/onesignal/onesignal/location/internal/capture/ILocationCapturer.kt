package com.onesignal.onesignal.location.internal.capture

interface ILocationCapturer {
    var locationCoarse: Boolean
    fun captureLastLocation()
}