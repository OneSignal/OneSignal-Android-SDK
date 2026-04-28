package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.jwt.JwtRequirement
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

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

    test("HYDRATE with REQUIRED forwards ivRequired=true") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val (service, newModel) = makeService(JwtRequirement.REQUIRED, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.HYDRATE)

        verify(exactly = 1) { operationRepo.onJwtConfigHydrated(true) }
    }

    test("HYDRATE with NOT_REQUIRED forwards ivRequired=false") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val (service, newModel) = makeService(JwtRequirement.NOT_REQUIRED, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.HYDRATE)

        verify(exactly = 1) { operationRepo.onJwtConfigHydrated(false) }
    }

    test("HYDRATE with UNKNOWN forwards ivRequired=false") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val (service, newModel) = makeService(JwtRequirement.UNKNOWN, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.HYDRATE)

        verify(exactly = 1) { operationRepo.onJwtConfigHydrated(false) }
    }

    test("non-HYDRATE model replacement is ignored") {
        val operationRepo = mockk<IOperationRepo>(relaxed = true)
        val (service, newModel) = makeService(JwtRequirement.REQUIRED, operationRepo)

        service.onModelReplaced(newModel, ModelChangeTags.NORMAL)

        verify(exactly = 0) { operationRepo.onJwtConfigHydrated(any()) }
    }
})
