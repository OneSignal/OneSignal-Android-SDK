package com.onesignal.core.internal.device.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IConnectivityMonitor
import com.onesignal.debug.internal.logging.Logging

internal class ConnectivityMonitor(
    private val applicationService: IApplicationService,
) : IConnectivityMonitor {
    @Suppress("TooGenericExceptionCaught")
    override fun addOnlineHandler(onOnline: () -> Unit) {
        try {
            val connectivityManager =
                applicationService.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return
            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivityManager.registerNetworkCallback(request, OnlineCallback(isOnline(connectivityManager), onOnline))
        } catch (t: Throwable) {
            // Can throw, e.g. when the app already holds the maximum number of network callbacks.
            Logging.warn("ConnectivityMonitor: unable to register network callback", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun isOnline(connectivityManager: ConnectivityManager) = connectivityManager.activeNetworkInfo?.isConnected == true

    // onAvailable also fires for networks already up at registration. Seeding with the current
    // state keeps those from counting as coming online.
    private class OnlineCallback(
        private var online: Boolean,
        private val onOnline: () -> Unit,
    ) : ConnectivityManager.NetworkCallback() {
        private val networks = mutableSetOf<Network>()

        override fun onAvailable(network: Network) {
            val cameOnline =
                synchronized(this) {
                    networks.add(network)
                    val wasOnline = online
                    online = true
                    !wasOnline
                }
            if (cameOnline) onOnline()
        }

        override fun onLost(network: Network) {
            synchronized(this) {
                networks.remove(network)
                if (networks.isEmpty()) online = false
            }
        }
    }
}
