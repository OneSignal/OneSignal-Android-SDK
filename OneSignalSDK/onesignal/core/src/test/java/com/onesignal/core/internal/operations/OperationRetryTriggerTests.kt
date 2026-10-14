package com.onesignal.core.internal.operations

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

private class TriggerMocks {
    val lifecycleHandler = slot<IApplicationLifecycleHandler>()
    val applicationService =
        mockk<IApplicationService> {
            every { addApplicationLifecycleHandler(capture(lifecycleHandler)) } just runs
        }
    val operationRepo = mockk<IOperationRepo>(relaxed = true)

    fun start() = OperationRetryTrigger(applicationService, operationRepo).start()
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
})
