package com.onesignal.user.internal.migrations

import com.onesignal.common.threading.Waiter
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepoLoadedListener
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.ExecutorMocks
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify

class RecoverFromDroppedLoginBugTests : FunSpec({
    test("ensure RecoverFromDroppedLoginBug receive onOperationRepoLoaded callback from operationRepo") {
        // Given
        val mockOperationModelStore = mockk<OperationModelStore>()
        val mockConfigModelStore = mockk<ConfigModelStore>()
        val operationRepo =
            spyk(
                OperationRepo(
                    listOf(),
                    mockOperationModelStore,
                    mockConfigModelStore,
                    Time(),
                    ExecutorMocks.getNewRecordState(mockConfigModelStore),
                ),
            )
        every { mockOperationModelStore.loadOperations() } just runs
        every { mockOperationModelStore.list() } returns listOf()
        val recovery = spyk(RecoverFromDroppedLoginBug(operationRepo, MockHelper.identityModelStore(), mockConfigModelStore))

        // When
        recovery.start()
        val waiter = Waiter()
        operationRepo.addOperationLoadedListener(
            object : IOperationRepoLoadedListener {
                override fun onOperationRepoLoaded() {
                    waiter.wake()
                }
            },
        )
        operationRepo.start()
        // Waiting here ensures recovery.onOperationRepoLoaded() is called consistently
        waiter.waitForWake()

        // Then
        verify(exactly = 1) {
            operationRepo.subscribe(recovery)
            recovery.onOperationRepoLoaded()
        }
    }
})
