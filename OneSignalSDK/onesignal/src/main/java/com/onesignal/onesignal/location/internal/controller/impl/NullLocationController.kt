package com.onesignal.onesignal.location.internal.controller.impl

import android.location.Location
import com.onesignal.onesignal.location.internal.controller.ILocationController
import com.onesignal.onesignal.location.internal.controller.ILocationUpdatedHandler

/**
 * This location controller is used when the device doesn't support location services.
 */
class NullLocationController : ILocationController {
    override suspend fun start() : Boolean { return false }
    override suspend fun stop() {}
    override fun getLastLocation(): Location? = null
    override fun subscribe(handler: ILocationUpdatedHandler) {}
    override fun unsubscribe(handler: ILocationUpdatedHandler) {}
}