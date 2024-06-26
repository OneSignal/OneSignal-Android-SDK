package com.onesignal.location.internal

import com.onesignal.core.internal.minification.KeepStub
import com.onesignal.location.ILocationManager

/**
 * The misconfigured IAMManager is an implementation of [ILocationManager] that warns the dev they
 * have not included the appropriate location module.
 */
@KeepStub
internal class MisconfiguredLocationManager : ILocationManager {
    override var isShared: Boolean
        get() = throw EXCEPTION
        set(value) = throw EXCEPTION

    override suspend fun requestPermission() = throw EXCEPTION

    companion object {
        private val EXCEPTION get() = Exception("Must include gradle module com.onesignal:Location in order to use this functionality!")
    }
}
