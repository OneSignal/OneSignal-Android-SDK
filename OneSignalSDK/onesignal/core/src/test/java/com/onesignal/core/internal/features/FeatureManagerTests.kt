package com.onesignal.core.internal.features

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.threading.ThreadingMode
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
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
    }

    fun stubConfigModel(model: ConfigModel) {
        every { model.sdkRemoteFeatureFlags } returns emptyList()
        every { model.sdkRemoteFeatureFlagMetadata } returns null
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
})
