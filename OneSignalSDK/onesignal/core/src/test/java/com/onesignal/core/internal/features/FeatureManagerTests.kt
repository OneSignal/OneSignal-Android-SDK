package com.onesignal.core.internal.features

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.threading.ThreadingMode
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.user.internal.jwt.IdentityVerificationGates
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.serialization.json.jsonPrimitive

class FeatureManagerTests : FunSpec({
    beforeEach {
        ThreadingMode.useBackgroundThreading = false
        IdentityVerificationGates.update(false, null, "test-reset")
    }

    fun stubConfigModel(model: ConfigModel) {
        every { model.sdkRemoteFeatureFlags } returns emptyList()
        every { model.sdkRemoteFeatureFlagMetadata } returns null
        every { model.useIdentityVerification } returns null
    }

    test("initial state enables BACKGROUND_THREADING when key is present in sdk remote flags") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_BACKGROUND_THREADING.key)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.SDK_BACKGROUND_THREADING) shouldBe true
        ThreadingMode.useBackgroundThreading shouldBe true
    }

    test("initial state enables BACKGROUND_THREADING when remote key differs only by letter case") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf("SDK_Background_Threading")
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.SDK_BACKGROUND_THREADING) shouldBe true
        ThreadingMode.useBackgroundThreading shouldBe true
    }

    test("onModelReplaced should not switch threading mode after startup") {
        // Given
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs
        val manager = FeatureManager(configModelStore)

        val updatedModel = mockk<ConfigModel>()
        stubConfigModel(updatedModel)
        every { updatedModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_BACKGROUND_THREADING.key)

        // When
        manager.onModelReplaced(updatedModel, ModelChangeTags.HYDRATE)

        // Then
        manager.isEnabled(FeatureFlag.SDK_BACKGROUND_THREADING) shouldBe false
        ThreadingMode.useBackgroundThreading shouldBe false
    }

    test("onModelUpdated should not switch threading mode after startup") {
        // Given
        val model = mockk<ConfigModel>()
        stubConfigModel(model)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns model
        every { configModelStore.subscribe(any()) } just runs
        val manager = FeatureManager(configModelStore)

        every { model.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_BACKGROUND_THREADING.key)

        // When
        manager.onModelUpdated(
            args = mockk {
                every { property } returns ConfigModel::sdkRemoteFeatureFlags.name
            },
            tag = ModelChangeTags.NORMAL
        )

        // Then
        manager.isEnabled(FeatureFlag.SDK_BACKGROUND_THREADING) shouldBe false
        ThreadingMode.useBackgroundThreading shouldBe false
    }

    test("onModelUpdated should keep startup mode when initial mode is enabled") {
        // Given
        val model = mockk<ConfigModel>()
        stubConfigModel(model)
        every { model.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_BACKGROUND_THREADING.key)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns model
        every { configModelStore.subscribe(any()) } just runs
        val manager = FeatureManager(configModelStore)

        every { model.sdkRemoteFeatureFlags } returns emptyList()

        // When
        manager.onModelUpdated(
            args = mockk {
                every { property } returns ConfigModel::sdkRemoteFeatureFlags.name
            },
            tag = ModelChangeTags.NORMAL
        )

        // Then
        manager.isEnabled(FeatureFlag.SDK_BACKGROUND_THREADING) shouldBe true
        ThreadingMode.useBackgroundThreading shouldBe true
    }

    test("remoteFeatureFlagMetadata returns parsed JSON from config") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlagMetadata } returns """{"X":{"note":"y"}}"""
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        val meta = requireNotNull(manager.remoteFeatureFlagMetadata())
        requireNotNull(meta.getValue("X")["note"]).jsonPrimitive.content shouldBe "y"
    }

    test("remoteFeatureFlagMetadata is null when config has no stored metadata") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlagMetadata } returns null
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        FeatureManager(configModelStore).remoteFeatureFlagMetadata() shouldBe null
    }

    test("initial state: IDENTITY_VERIFICATION flag off + jwt_required=null → gates both false") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.IDENTITY_VERIFICATION) shouldBe false
        IdentityVerificationGates.newCodePathsRun shouldBe false
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("initial state: IDENTITY_VERIFICATION flag on → newCodePathsRun=true, ivBehaviorActive=false") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.IDENTITY_VERIFICATION.key)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.IDENTITY_VERIFICATION) shouldBe true
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }

    test("ERROR STATE: flag off + jwt_required=true → both gates true (customer config wins)") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.useIdentityVerification } returns true
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.IDENTITY_VERIFICATION) shouldBe false
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("initial state: flag on + jwt_required=true → full IV (both gates true)") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.IDENTITY_VERIFICATION.key)
        every { initialModel.useIdentityVerification } returns true
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.IDENTITY_VERIFICATION) shouldBe true
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("HYDRATE updates gates when useIdentityVerification arrives as true") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs
        val manager = FeatureManager(configModelStore)

        IdentityVerificationGates.ivBehaviorActive shouldBe false

        val updatedModel = mockk<ConfigModel>()
        stubConfigModel(updatedModel)
        every { updatedModel.useIdentityVerification } returns true

        manager.onModelReplaced(updatedModel, ModelChangeTags.HYDRATE)

        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe true
    }

    test("IDENTITY_VERIFICATION is IMMEDIATE: mid-session flag flip flows through to the gates") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs
        val manager = FeatureManager(configModelStore)

        // Mid-session model replacement enables the flag remotely.
        val updatedModel = mockk<ConfigModel>()
        stubConfigModel(updatedModel)
        every { updatedModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.IDENTITY_VERIFICATION.key)
        every { updatedModel.useIdentityVerification } returns false

        manager.onModelReplaced(updatedModel, ModelChangeTags.HYDRATE)

        // Feature flag flips in-memory because IDENTITY_VERIFICATION is IMMEDIATE.
        manager.isEnabled(FeatureFlag.IDENTITY_VERIFICATION) shouldBe true
        // newCodePathsRun reflects the flipped flag; ivBehaviorActive still false (jwt_required=false).
        IdentityVerificationGates.newCodePathsRun shouldBe true
        IdentityVerificationGates.ivBehaviorActive shouldBe false
    }
})
