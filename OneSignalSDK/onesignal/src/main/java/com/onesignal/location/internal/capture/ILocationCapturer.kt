package com.onesignal.location.internal.capture

interface ILocationCapturer {
    var locationCoarse: Boolean
    fun captureLastLocation()
}
