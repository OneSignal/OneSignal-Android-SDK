package com.onesignal.user.internal.migrations

import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.operations.ExecutorMocks
import com.onesignal.user.internal.operations.LoginUserOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.withTimeout

private class Mocks {
    val operationModelStore: OperationModelStore =
        run {
            val mockOperationModelStore = mockk<OperationModelStore>()
            every { mockOperationModelStore.loadOperations() } just runs
            every { mockOperationModelStore.list() } returns listOf()
            every { mockOperationModelStore.add(any()) } just runs
            every { mockOperationModelStore.remove(any()) } just runs
            mockOperationModelStore
        }
    val configModelStore = MockHelper.configModelStore()
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

    var oneSignalId = "local-id"
    val identityModelStore by lazy {
        MockHelper.identityModelStore {
            it.onesignalId = oneSignalId
            it.externalId = "myExtId"
        }
    }
    val recovery = spyk(RecoverFromDroppedLoginBug(operationRepo, identityModelStore, configModelStore))

    val expectedOperation by lazy {
        LoginUserOperation(
            configModelStore.model.appId,
            identityModelStore.model.onesignalId,
            identityModelStore.model.externalId,
            null,
        )
    }

    fun verifyExpectedLoginOperation(expectedOp: LoginUserOperation = expectedOperation) {
        verify(exactly = 1) {
            operationRepo.enqueue(
                withArg {
                    (it is LoginUserOperation) shouldBe true
                    val op = it as LoginUserOperation
                    op.appId shouldBe expectedOp.appId
                    op.externalId shouldBe expectedOp.externalId
                    op.existingOnesignalId shouldBe expectedOp.existingOnesignalId
                    op.onesignalId shouldBe expectedOp.onesignalId
                },
            )
        }
    }
}

class RecoverFromDroppedLoginBugTests : FunSpec({

    listener(IOMockHelper)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("ensure it adds missing operation") {
        // Given
        val mocks = Mocks()

        // When
        mocks.recovery.start()
        mocks.operationRepo.start()
        mocks.operationRepo.awaitInitialized()

        // Then
        mocks.verifyExpectedLoginOperation()
    }

    test("ensure it adds missing operation, even if operationRepo is already initialized") {
        // Given
        val mocks = Mocks()

        // When
        mocks.operationRepo.start()
        // give operation repo some time to fully initialize
        awaitIO()

        mocks.recovery.start()
        withTimeout(1_000) { mocks.operationRepo.awaitInitialized() }

        // Then
        mocks.verifyExpectedLoginOperation()
    }
})
