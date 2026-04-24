package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.jwt.JwtRequirement
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class IdentityVerificationServiceTests : FunSpec({
    beforeEach { Logging.logLevel = LogLevel.NONE }

    fun makeService(
        requirement: JwtRequirement,
        operationRepo: IOperationRepo,
    ): Pair<IdentityVerificationService, ConfigModel> {
        val configModel = mockk<ConfigModel>(relaxed = true)
        every { configModel.useIdentityVerification } returns requirement
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns configModel
        every { configModelStore.subscribe(any()) } just runs
        val service = IdentityVerificationService(configModelStore, operationRepo)
        return service to configModel
    }

    test("start subscribes to ConfigModelStore") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.subscribe(any()) } just runs
        val service = IdentityVerificationService(configModelStore, operationRepo)

        service.start()

        verify(exactly = 1) { configModelStore.subscribe(service) }
    }

    test("HYDRATE with REQUIRED purges anon ops then wakes the queue (after awaitInitialized)") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        coEvery { operationRepo.awaitInitialized() } just runs
        every { operationRepo.removeOperationsWithoutExternalId() } just runs
        every { operationRepo.forceExecuteOperations() } just runs
        val (service, newModel) = makeService(JwtRequirement.REQUIRED, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.HYDRATE)

        // onModelReplaced launches on IO; wait for the side effects.
        runBlocking {
            withTimeoutOrNull(2_000) {
                while (true) {
                    try {
                        coVerify(exactly = 1) { operationRepo.removeOperationsWithoutExternalId() }
                        verify(exactly = 1) { operationRepo.forceExecuteOperations() }
                        break
                    } catch (_: AssertionError) {
                        delay(20)
                    }
                }
            }
        }

        // Purge must come after awaitInitialized — the whole point of the race fix.
        coVerifyOrder {
            operationRepo.awaitInitialized()
            operationRepo.removeOperationsWithoutExternalId()
            operationRepo.forceExecuteOperations()
        }
    }

    test("HYDRATE with NOT_REQUIRED wakes the queue but does NOT purge") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        coEvery { operationRepo.awaitInitialized() } just runs
        every { operationRepo.forceExecuteOperations() } just runs
        val (service, newModel) = makeService(JwtRequirement.NOT_REQUIRED, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.HYDRATE)

        runBlocking {
            withTimeoutOrNull(2_000) {
                while (true) {
                    try {
                        verify(exactly = 1) { operationRepo.forceExecuteOperations() }
                        break
                    } catch (_: AssertionError) {
                        delay(20)
                    }
                }
            }
        }
        verify(exactly = 0) { operationRepo.removeOperationsWithoutExternalId() }
    }

    test("HYDRATE with UNKNOWN wakes the queue (no purge) — rare but benign") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        coEvery { operationRepo.awaitInitialized() } just runs
        every { operationRepo.forceExecuteOperations() } just runs
        val (service, newModel) = makeService(JwtRequirement.UNKNOWN, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.HYDRATE)

        runBlocking {
            withTimeoutOrNull(2_000) {
                while (true) {
                    try {
                        verify(exactly = 1) { operationRepo.forceExecuteOperations() }
                        break
                    } catch (_: AssertionError) {
                        delay(20)
                    }
                }
            }
        }
        verify(exactly = 0) { operationRepo.removeOperationsWithoutExternalId() }
    }

    test("non-HYDRATE model replacement is ignored") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val (service, newModel) = makeService(JwtRequirement.REQUIRED, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.NORMAL)

        // Give any errant coroutine a chance to schedule; verify nothing was called.
        runBlocking { delay(50) }
        verify(exactly = 0) { operationRepo.forceExecuteOperations() }
        verify(exactly = 0) { operationRepo.removeOperationsWithoutExternalId() }
        coVerify(exactly = 0) { operationRepo.awaitInitialized() }
    }
})
