package com.onesignal.session.internal.session.queue.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.onesignal.core.internal.application.ApplicationLifecycleHandlerBase
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging

/**
 * Calls [onRetry] when the app is foregrounded, and when a network becomes available while it is
 * foregrounded. Android 15+ blocks network access for cached background apps, so a background
 * attempt would only fail and lengthen the backoff.
 */
internal class SessionsApiRetryTrigger(
    private val applicationService: IApplicationService,
    private val onRetry: () -> Unit,
) {
    fun start() {
        applicationService.addApplicationLifecycleHandler(
            object : ApplicationLifecycleHandlerBase() {
                override fun onFocus(firedOnSubscribe: Boolean) = onRetry()
            },
        )
        try {
            val connectivityManager =
                applicationService.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        if (applicationService.isInForeground) onRetry()
                    }
                },
            )
        } catch (e: RuntimeException) {
            Logging.warn("SessionsApiRetryTrigger: unable to watch for network changes", e)
        }
    }
}
