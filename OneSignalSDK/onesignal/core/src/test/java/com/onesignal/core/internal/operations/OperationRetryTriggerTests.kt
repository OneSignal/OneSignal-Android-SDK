package com.onesignal.core.internal.operations

import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IConnectivityMonitor
import com.onesignal.core.internal.operations.impl.OperationRetryTrigger
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

private class TriggerMocks {
    val lifecycleHandler = slot<IApplicationLifecycleHandler>()
    val onOnline = slot<() -> Unit>()
    var isInForeground = true
    val applicationService =
        mockk<IApplicationService> {
            every { addApplicationLifecycleHandler(capture(lifecycleHandler)) } just runs
            every { isInForeground } answers { this@TriggerMocks.isInForeground }
        }
    val connectivityMonitor =
        mockk<IConnectivityMonitor> {
            every { addOnlineHandler(capture(onOnline)) } just runs
        }
    val operationRepo = mockk<IOperationRepo>(relaxed = true)

    fun start() = OperationRetryTrigger(applicationService, connectivityMonitor, operationRepo).start()
}

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

    test("connectivity is not watched until the app first comes to the foreground, then only once") {
        val mocks = TriggerMocks()
        mocks.start()
        verify(exactly = 0) { mocks.connectivityMonitor.addOnlineHandler(any()) }

        mocks.lifecycleHandler.captured.onFocus(firedOnSubscribe = true)
        mocks.lifecycleHandler.captured.onFocus(firedOnSubscribe = false)

        verify(exactly = 1) { mocks.connectivityMonitor.addOnlineHandler(any()) }
    }

    test("coming online in the foreground forces queued operations to retry") {
        val mocks = TriggerMocks()
        mocks.start()
        mocks.lifecycleHandler.captured.onFocus(firedOnSubscribe = true)

        mocks.onOnline.captured()

        verify(exactly = 1) { mocks.operationRepo.forceExecuteOperations() }
    }

    test("coming online in the background does not force a retry") {
        val mocks = TriggerMocks()
        mocks.start()
        mocks.lifecycleHandler.captured.onFocus(firedOnSubscribe = true)
        mocks.isInForeground = false

        mocks.onOnline.captured()

        verify(exactly = 0) { mocks.operationRepo.forceExecuteOperations() }
    }
})
