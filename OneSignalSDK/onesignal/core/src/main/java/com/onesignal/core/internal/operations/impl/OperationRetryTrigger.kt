package com.onesignal.core.internal.operations.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging

/**
 * Retries operations waiting on failure backoff when the app opens or the network returns,
 * rather than leaving them until the backoff runs out.
 */
internal class OperationRetryTrigger(
    private val applicationService: IApplicationService,
    private val operationRepo: IOperationRepo,
) : IStartableService, IApplicationLifecycleHandler {
    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
        registerNetworkCallback()
    }

    override fun onFocus(firedOnSubscribe: Boolean) = operationRepo.retryNow()

    override fun onUnfocused() = Unit

    private fun registerNetworkCallback() {
        val connectivityManager =
            applicationService.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request =
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        try {
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = operationRepo.retryNow()
                },
            )
        } catch (e: RuntimeException) {
            // Throws SecurityException on some OEM builds and when the per-app callback limit is reached.
            Logging.warn("OperationRetryTrigger: unable to register a network callback", e)
        }
    }
}
