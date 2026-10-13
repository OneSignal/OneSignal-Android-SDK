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
 * Ends the retry backoff of queued operations when the app comes to the foreground
 * or a network becomes available.
 */
internal class OperationRetryTrigger(
    private val applicationService: IApplicationService,
    private val operationRepo: IOperationRepo,
) : IStartableService, IApplicationLifecycleHandler {
    override fun start() {
        applicationService.addApplicationLifecycleHandler(this)
        registerNetworkCallback()
    }

    override fun onFocus(firedOnSubscribe: Boolean) {
        if (firedOnSubscribe) return
        operationRepo.forceExecuteOperations()
    }

    override fun onUnfocused() {}

    @Suppress("TooGenericExceptionCaught")
    private fun registerNetworkCallback() {
        try {
            val connectivityManager =
                applicationService.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return
            val request =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        operationRepo.forceExecuteOperations()
                    }
                },
            )
        } catch (t: Throwable) {
            // Can throw, e.g. when the app already holds the maximum number of network callbacks.
            Logging.warn("OperationRetryTrigger: unable to register network callback", t)
        }
    }
}
