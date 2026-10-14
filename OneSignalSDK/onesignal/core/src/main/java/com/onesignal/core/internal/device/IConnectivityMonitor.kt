package com.onesignal.core.internal.device

internal interface IConnectivityMonitor {
    /**
     * Calls [onOnline] each time the device goes from having no internet-capable network to
     * having one. Not called for the state at registration.
     */
    fun addOnlineHandler(onOnline: () -> Unit)
}
