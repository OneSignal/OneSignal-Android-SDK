package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.features.FeatureFlag
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.jwt.JwtRequirement
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

class IdentityVerificationServiceTests : FunSpec({
    beforeEach { Logging.logLevel = LogLevel.NONE }

    fun makeService(
        featureFlagOn: Boolean = false,
        requirement: JwtRequirement = JwtRequirement.UNKNOWN,
    ): Triple<IdentityVerificationService, ConfigModel, ConfigModelStore> {
        val featureManager = mockk<IFeatureManager>()
        every { featureManager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) } returns featureFlagOn

        val configModel = mockk<ConfigModel>(relaxed = true)
        every { configModel.useIdentityVerification } returns requirement

        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns configModel
        every { configModelStore.subscribe(any()) } just runs

        val service = IdentityVerificationService(featureManager, configModelStore)
        return Triple(service, configModel, configModelStore)
    }

    test("start subscribes to ConfigModelStore") {
        val (service, _, configModelStore) = makeService()

        service.start()

        verify(exactly = 1) { configModelStore.subscribe(service) }
    }

    // --- Gate derivation -------------------------------------------------------------------

    test("flag off + UNKNOWN: both gates false (safe pre-HYDRATE default)") {
        val (service, _, _) = makeService(featureFlagOn = false, requirement = JwtRequirement.UNKNOWN)
        service.newCodePathsRun shouldBe false
        service.ivBehaviorActive shouldBe false
    }

    test("flag off + NOT_REQUIRED: both gates false") {
        val (service, _, _) = makeService(featureFlagOn = false, requirement = JwtRequirement.NOT_REQUIRED)
        service.newCodePathsRun shouldBe false
        service.ivBehaviorActive shouldBe false
    }

    test("ERROR STATE — flag off + REQUIRED: both gates true (customer config wins)") {
        val (service, _, _) = makeService(featureFlagOn = false, requirement = JwtRequirement.REQUIRED)
        service.newCodePathsRun shouldBe true
        service.ivBehaviorActive shouldBe true
    }

    test("flag on + UNKNOWN: newCodePathsRun true, ivBehaviorActive false") {
        val (service, _, _) = makeService(featureFlagOn = true, requirement = JwtRequirement.UNKNOWN)
        service.newCodePathsRun shouldBe true
        service.ivBehaviorActive shouldBe false
    }

    test("flag on + NOT_REQUIRED: newCodePathsRun true, ivBehaviorActive false (Phase 3)") {
        val (service, _, _) = makeService(featureFlagOn = true, requirement = JwtRequirement.NOT_REQUIRED)
        service.newCodePathsRun shouldBe true
        service.ivBehaviorActive shouldBe false
    }

    test("flag on + REQUIRED: both gates true (full IV)") {
        val (service, _, _) = makeService(featureFlagOn = true, requirement = JwtRequirement.REQUIRED)
        service.newCodePathsRun shouldBe true
        service.ivBehaviorActive shouldBe true
    }

    test("gates are derived on read — config flip is reflected without explicit update") {
        val (service, configModel, _) = makeService(featureFlagOn = false, requirement = JwtRequirement.UNKNOWN)
        service.ivBehaviorActive shouldBe false

        every { configModel.useIdentityVerification } returns JwtRequirement.REQUIRED

        service.ivBehaviorActive shouldBe true
        service.newCodePathsRun shouldBe true
    }

    // --- HYDRATE forwarding ----------------------------------------------------------------

    test("HYDRATE with REQUIRED invokes registered handler with ivRequired=true") {
        val (service, model, _) = makeService(requirement = JwtRequirement.REQUIRED)
        var fired: Boolean? = null
        service.setOnJwtConfigHydratedHandler { fired = it }

        service.onModelReplaced(model, ModelChangeTags.HYDRATE)

        fired shouldBe true
    }

    test("HYDRATE with NOT_REQUIRED invokes registered handler with ivRequired=false") {
        val (service, model, _) = makeService(requirement = JwtRequirement.NOT_REQUIRED)
        var fired: Boolean? = null
        service.setOnJwtConfigHydratedHandler { fired = it }

        service.onModelReplaced(model, ModelChangeTags.HYDRATE)

        fired shouldBe false
    }

    test("non-HYDRATE replacement is ignored") {
        val (service, model, _) = makeService(requirement = JwtRequirement.REQUIRED)
        var fired: Boolean? = null
        service.setOnJwtConfigHydratedHandler { fired = it }

        service.onModelReplaced(model, ModelChangeTags.NORMAL)

        fired shouldBe null
    }

    test("no handler registered: HYDRATE is a no-op (no NPE)") {
        val (service, model, _) = makeService(requirement = JwtRequirement.REQUIRED)
        // Don't register a handler.
        service.onModelReplaced(model, ModelChangeTags.HYDRATE)
    }
})
