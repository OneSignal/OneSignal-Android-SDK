package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.location.internal.ILocationController

class NullLocationController : ILocationController {
    override fun start() {}
    override fun onFocusChange() {}
    override fun stop() {}
}