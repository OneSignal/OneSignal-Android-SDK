package com.onesignal.core.internal.features

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.user.internal.jwt.JwtRequirement
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.serialization.json.jsonPrimitive

class FeatureManagerTests : FunSpec({
    fun stubConfigModel(model: ConfigModel) {
        every { model.sdkRemoteFeatureFlags } returns emptyList()
        every { model.sdkRemoteFeatureFlagMetadata } returns null
        every { model.useIdentityVerification } returns JwtRequirement.UNKNOWN
    }

    test("isEnabled is false when the key is not present in sdk remote flags") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) shouldBe false
    }

    test("initial state enables a feature when its key is present in sdk remote flags") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) shouldBe true
    }

    test("initial state enables a feature when the remote key differs only by letter case") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf("SDK_Identity_Verification")
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) shouldBe true
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

    test("enabledFeatureKeys is empty when no flags are enabled") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.enabledFeatureKeys() shouldBe emptyList()
    }

    test("enabledFeatureKeys returns canonical key when a flag is enabled at startup") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        every { initialModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs

        val manager = FeatureManager(configModelStore)

        manager.enabledFeatureKeys() shouldBe listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key)
    }

    test("IDENTITY_VERIFICATION is IMMEDIATE: mid-session flag flip flows through isEnabled") {
        val initialModel = mockk<ConfigModel>()
        stubConfigModel(initialModel)
        val configModelStore = mockk<ConfigModelStore>()
        every { configModelStore.model } returns initialModel
        every { configModelStore.subscribe(any()) } just runs
        val manager = FeatureManager(configModelStore)

        manager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) shouldBe false

        // Mid-session model replacement enables the flag remotely.
        val updatedModel = mockk<ConfigModel>()
        stubConfigModel(updatedModel)
        every { updatedModel.sdkRemoteFeatureFlags } returns listOf(FeatureFlag.SDK_IDENTITY_VERIFICATION.key)
        every { configModelStore.model } returns updatedModel

        manager.onModelReplaced(updatedModel, ModelChangeTags.HYDRATE)

        // Feature flag flips in-memory because IDENTITY_VERIFICATION is IMMEDIATE.
        manager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) shouldBe true
    }
})
