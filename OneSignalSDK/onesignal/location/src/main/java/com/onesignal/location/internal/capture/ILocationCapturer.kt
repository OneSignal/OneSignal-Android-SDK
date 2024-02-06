package com.onesignal.location.internal.capture

internal interface ILocationCapturer {
    var locationCoarse: Boolean

    fun captureLastLocation()
}
