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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

private class Mocks {
    val operationModelStore: OperationModelStore =
        run {
            val mockOperationModelStore = mockk<OperationModelStore>()
            every { mockOperationModelStore.loadOperations() } just runs
            every { mockOperationModelStore.list() } returns listOf()
            mockOperationModelStore
        }
    val configModelStore = mockk<ConfigModelStore>()
    val operationRepo =
        spyk(
            OperationRepo(
                listOf(),
                operationModelStore,
                configModelStore,
                Time(),
                ExecutorMocks.getNewRecordState(configModelStore),
            ),
        )

    val recovery = spyk(RecoverFromDroppedLoginBug(operationRepo, MockHelper.identityModelStore(), configModelStore))
}

class RecoverFromDroppedLoginBugTests : FunSpec({
    test("ensure onOperationRepoLoaded callback fires from operationRepo") {
        // Given
        val mocks = Mocks()

        // When
        mocks.recovery.start()
        val waiter = Waiter()
        mocks.operationRepo.addOperationLoadedListener(
            object : IOperationRepoLoadedListener {
                override fun onOperationRepoLoaded() {
                    waiter.wake()
                }
            },
        )
        mocks.operationRepo.start()
        // Waiting here ensures recovery.onOperationRepoLoaded() is called consistently
        waiter.waitForWake()

        // Then
        verify(exactly = 1) {
            mocks.operationRepo.subscribe(mocks.recovery)
            mocks.recovery.onOperationRepoLoaded()
        }
    }

    test("ensure onOperationRepoLoaded callback fires from operationRepo, even if started first") {
        // Given
        val mocks = Mocks()

        // When
        mocks.operationRepo.start()
        // give operation repo some time to fully initialize
        delay(200)

        mocks.recovery.start()

        val waiter = Waiter()
        mocks.operationRepo.addOperationLoadedListener(
            object : IOperationRepoLoadedListener {
                override fun onOperationRepoLoaded() {
                    waiter.wake()
                }
            },
        )
        // Waiting here ensures recovery.onOperationRepoLoaded() is called consistently
        withTimeout(1_000) { waiter.waitForWake() }

        // Then
        verify(exactly = 1) {
            mocks.operationRepo.subscribe(mocks.recovery)
            mocks.recovery.onOperationRepoLoaded()
        }
    }
})
