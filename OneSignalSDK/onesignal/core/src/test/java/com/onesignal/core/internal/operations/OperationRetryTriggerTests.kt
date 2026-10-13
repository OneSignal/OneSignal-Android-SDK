package com.onesignal.core.internal.operations

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.operations.impl.OperationRetryTrigger
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.robolectric.annotation.Config

private class TriggerMocks {
    val lifecycleHandler = slot<IApplicationLifecycleHandler>()
    val networkCallback = slot<ConnectivityManager.NetworkCallback>()
    val connectivityManager =
        mockk<ConnectivityManager> {
            every { registerNetworkCallback(any<NetworkRequest>(), capture(networkCallback)) } just runs
        }
    val applicationService =
        mockk<IApplicationService> {
            every { addApplicationLifecycleHandler(capture(lifecycleHandler)) } just runs
            every { appContext } returns
                mockk<Context> {
                    every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
                }
        }
    val operationRepo = mockk<IOperationRepo>(relaxed = true)

    fun start() = OperationRetryTrigger(applicationService, operationRepo).start()
}

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OperationRetryTriggerTests : FunSpec({
    test("coming to the foreground forces queued operations to retry") {
        val mocks = TriggerMocks()
        mocks.start()

        mocks.lifecycleHandler.captured.onFocus(firedOnSubscribe = false)

        verify(exactly = 1) { mocks.operationRepo.forceExecuteOperations() }
    }

    test("focus reported on subscribe does not force a retry") {
        val mocks = TriggerMocks()
        mocks.start()

        mocks.lifecycleHandler.captured.onFocus(firedOnSubscribe = true)

        verify(exactly = 0) { mocks.operationRepo.forceExecuteOperations() }
    }

    test("network becoming available forces queued operations to retry") {
        val mocks = TriggerMocks()
        mocks.start()

        mocks.networkCallback.captured.onAvailable(mockk<Network>())

        verify(exactly = 1) { mocks.operationRepo.forceExecuteOperations() }
    }

    test("a failure registering the network callback does not crash start") {
        val mocks = TriggerMocks()
        every { mocks.connectivityManager.registerNetworkCallback(any<NetworkRequest>(), any<ConnectivityManager.NetworkCallback>()) } throws
            SecurityException()

        mocks.start()

        verify(exactly = 1) { mocks.applicationService.addApplicationLifecycleHandler(any()) }
    }
})
