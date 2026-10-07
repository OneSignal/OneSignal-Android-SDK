package com.onesignal.core.internal.operations

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.operations.impl.OperationRetryTrigger
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

private class ConnectivityContext(
    base: Context,
    private val connectivityManager: ConnectivityManager,
) : ContextWrapper(base) {
    override fun getSystemService(name: String): Any? =
        if (name == Context.CONNECTIVITY_SERVICE) connectivityManager else super.getSystemService(name)
}

@RobolectricTest
class OperationRetryTriggerTests : FunSpec({
    fun setUp(connectivityManager: ConnectivityManager): Pair<OperationRetryTrigger, IOperationRepo> {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val applicationService = mockk<IApplicationService>(relaxed = true)
        every { applicationService.appContext } returns ConnectivityContext(ApplicationProvider.getApplicationContext(), connectivityManager)
        return OperationRetryTrigger(applicationService, operationRepo) to operationRepo
    }

    test("retries when the network becomes available") {
        // Given
        val callback = slot<ConnectivityManager.NetworkCallback>()
        val connectivityManager = mockk<ConnectivityManager>()
        every { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), capture(callback)) } just runs
        val (trigger, operationRepo) = setUp(connectivityManager)
        trigger.start()

        // When
        callback.captured.onAvailable(mockk<Network>())

        // Then
        verify(exactly = 1) { operationRepo.retryNow() }
    }

    test("retries when the app comes to the foreground") {
        // Given
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val (trigger, operationRepo) = setUp(connectivityManager)

        // When
        trigger.onFocus(firedOnSubscribe = false)

        // Then
        verify(exactly = 1) { operationRepo.retryNow() }
    }

    test("starts even when the network callback cannot be registered") {
        // Given
        val connectivityManager = mockk<ConnectivityManager>()
        every { connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) } throws
            SecurityException("denied")
        val (trigger, operationRepo) = setUp(connectivityManager)

        // When
        trigger.start()
        trigger.onFocus(firedOnSubscribe = false)

        // Then
        verify(exactly = 1) { operationRepo.retryNow() }
    }
})
