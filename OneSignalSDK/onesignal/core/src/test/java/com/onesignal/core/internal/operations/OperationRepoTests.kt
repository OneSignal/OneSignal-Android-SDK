package com.onesignal.core.internal.operations

import com.onesignal.common.threading.Waiter
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.delay

// Mocks used by every test in this file
private class Mocks {
    val operationModelStore: OperationModelStore =
        run {
            val mockOperationModelStore = mockk<OperationModelStore>()
            every { mockOperationModelStore.list() } returns listOf()
            every { mockOperationModelStore.add(any()) } just runs
            every { mockOperationModelStore.remove(any()) } just runs
            mockOperationModelStore
        }

    val executor: IOperationExecutor =
        run {
            val mockExecutor = mockk<IOperationExecutor>()
            every { mockExecutor.operations } returns listOf("DUMMY_OPERATION")
            coEvery { mockExecutor.execute(any()) } returns ExecutionResponse(ExecutionResult.SUCCESS)
            mockExecutor
        }

    val operationRepo: OperationRepo =
        run {
            spyk(
                OperationRepo(
                    listOf(executor),
                    operationModelStore,
                    MockHelper.configModelStore(),
                    MockHelper.time(1000),
                ),
            )
        }
}

class OperationRepoTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    // Ensures we are not continuously waking the CPU
    test("ensure processQueueForever suspends when queue is empty") {
        // Given
        val mocks = Mocks()

        // When
        mocks.operationRepo.start()
        val response = mocks.operationRepo.enqueueAndWait(mockOperation())
        // Must wait for background logic to spin to see how many times it
        // will call getNextOps()
        delay(1_000)

        // Then
        response shouldBe true
        verify(exactly = 2) {
            // 1st: gets the operation
            // 2nd: will be empty
            // 3rd: shouldn't be called, loop should be waiting on next operation
            mocks.operationRepo.getNextOps()
        }
    }

    test("enqueue operation executes and is removed when executed") {
        // Given
        val mocks = Mocks()

        val operationIdSlot = slot<String>()
        val operation = mockOperation(operationIdSlot = operationIdSlot)

        // When
        mocks.operationRepo.start()
        val response = mocks.operationRepo.enqueueAndWait(operation)

        // Then
        response shouldBe true
        operationIdSlot.isCaptured shouldBe true
        coVerifyOrder {
            mocks.operationModelStore.add(operation)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation
                },
            )
            mocks.operationModelStore.remove("operationId")
        }
    }

    test("enqueue operation executes and is removed when executed after retry") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.operationRepo.delayBeforeRetry(any()) } just runs
        coEvery {
            mocks.executor.execute(any())
        } returns ExecutionResponse(ExecutionResult.FAIL_RETRY) andThen ExecutionResponse(ExecutionResult.SUCCESS)

        val operationIdSlot = slot<String>()
        val operation = mockOperation(operationIdSlot = operationIdSlot)

        // When
        mocks.operationRepo.start()
        val response = mocks.operationRepo.enqueueAndWait(operation)

        // Then
        response shouldBe true
        operationIdSlot.isCaptured shouldBe true
        coVerifyOrder {
            mocks.operationModelStore.add(operation)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation
                },
            )
            mocks.operationRepo.delayBeforeRetry(1)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation
                },
            )
            mocks.operationModelStore.remove("operationId")
        }
    }

    test("enqueue operation executes and is removed when executed after fail") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.executor.execute(any()) } returns ExecutionResponse(ExecutionResult.FAIL_NORETRY)

        val operationIdSlot = slot<String>()
        val operation = mockOperation(operationIdSlot = operationIdSlot)

        // When
        mocks.operationRepo.start()
        val response = mocks.operationRepo.enqueueAndWait(operation)

        // Then
        response shouldBe false
        operationIdSlot.isCaptured shouldBe true
        coVerifyOrder {
            mocks.operationModelStore.add(operation)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation
                },
            )
            mocks.operationModelStore.remove("operationId")
        }
    }

    test("enqueue 2 operations that cannot be grouped will be executed separately from each other") {
        // Given
        val mocks = Mocks()
        val waiter = Waiter()
        every { mocks.operationModelStore.remove(any()) } answers {} andThenAnswer { waiter.wake() }

        val operation1 = mockOperation("operationId1", groupComparisonType = GroupComparisonType.CREATE)
        val operation2 = mockOperation("operationId2", createComparisonKey = "create-key2")

        // When
        mocks.operationRepo.enqueue(operation1)
        mocks.operationRepo.enqueue(operation2)
        mocks.operationRepo.start()

        waiter.waitForWake()

        // Then
        coVerifyOrder {
            mocks.operationModelStore.add(operation1)
            mocks.operationModelStore.add(operation2)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation1
                },
            )
            mocks.operationModelStore.remove("operationId1")
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation2
                },
            )
            mocks.operationModelStore.remove("operationId2")
        }
    }

    test("enqueue 2 operations that can be grouped via create will be executed as a group") {
        // Given
        val mocks = Mocks()
        val waiter = Waiter()
        every { mocks.operationModelStore.remove(any()) } answers {} andThenAnswer { waiter.wake() }

        val operation1 = mockOperation("operationId1", groupComparisonType = GroupComparisonType.CREATE)
        val operation2 = mockOperation("operationId2")

        // When
        mocks.operationRepo.enqueue(operation1)
        mocks.operationRepo.enqueue(operation2)
        mocks.operationRepo.start()

        waiter.waitForWake()

        // Then
        coVerifyOrder {
            mocks.operationModelStore.add(operation1)
            mocks.operationModelStore.add(operation2)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 2
                    it[0] shouldBe operation1
                    it[1] shouldBe operation2
                },
            )
            mocks.operationModelStore.remove("operationId1")
            mocks.operationModelStore.remove("operationId2")
        }
    }

    test("enqueue 2 operations where the first cannot be executed but can be grouped via create will be executed as a group") {
        // Given
        val mocks = Mocks()
        val waiter = Waiter()
        every { mocks.operationModelStore.remove(any()) } answers {} andThenAnswer { waiter.wake() }

        val operation1 = mockOperation("operationId1", canStartExecute = false, groupComparisonType = GroupComparisonType.ALTER)
        val operation2 = mockOperation("operationId2", groupComparisonType = GroupComparisonType.CREATE)

        // When
        mocks.operationRepo.enqueue(operation1)
        mocks.operationRepo.enqueue(operation2)
        mocks.operationRepo.start()

        waiter.waitForWake()

        // Then
        coVerifyOrder {
            mocks.operationModelStore.add(operation1)
            mocks.operationModelStore.add(operation2)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 2
                    it[0] shouldBe operation2
                    it[1] shouldBe operation1
                },
            )
            mocks.operationModelStore.remove("operationId2")
            mocks.operationModelStore.remove("operationId1")
        }
    }

    test("execution of 1 operation with translation IDs will drive translateId of subsequence operations") {
        // Given
        val mocks = Mocks()
        val waiter = Waiter()
        coEvery {
            mocks.executor.execute(any())
        } returns ExecutionResponse(ExecutionResult.SUCCESS, mapOf("id1" to "id2")) andThen ExecutionResponse(ExecutionResult.SUCCESS)

        every { mocks.operationModelStore.remove(any()) } answers {} andThenAnswer { waiter.wake() }

        val operation1 = mockOperation("operationId1")
        val operation2 = mockOperation("operationId2")

        // When
        mocks.operationRepo.enqueue(operation1)
        mocks.operationRepo.enqueue(operation2)
        mocks.operationRepo.start()

        waiter.waitForWake()

        // Then
        coVerifyOrder {
            mocks.operationModelStore.add(operation1)
            mocks.operationModelStore.add(operation2)
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation1
                },
            )
            operation2.translateIds(mapOf("id1" to "id2"))
            mocks.operationModelStore.remove("operationId1")
            mocks.executor.execute(
                withArg {
                    it.count() shouldBe 1
                    it[0] shouldBe operation2
                },
            )
            mocks.operationModelStore.remove("operationId2")
        }
    }
}) {
    companion object {
        private fun mockOperation(
            id: String = "operationId",
            name: String = "DUMMY_OPERATION",
            canStartExecute: Boolean = true,
            groupComparisonType: GroupComparisonType = GroupComparisonType.NONE,
            createComparisonKey: String = "create-key",
            modifyComparisonKey: String = "modify-key",
            operationIdSlot: CapturingSlot<String>? = null,
        ): Operation {
            val operation = mockk<Operation>()
            val opIdSlot = operationIdSlot ?: slot()

            every { operation.name } returns name
            every { operation.id } returns id
            every { operation.id = capture(opIdSlot) } just runs
            every { operation.canStartExecute } returns canStartExecute
            every { operation.groupComparisonType } returns groupComparisonType
            every { operation.createComparisonKey } returns createComparisonKey
            every { operation.modifyComparisonKey } returns modifyComparisonKey
            every { operation.translateIds(any()) } just runs

            return operation
        }
    }
}
