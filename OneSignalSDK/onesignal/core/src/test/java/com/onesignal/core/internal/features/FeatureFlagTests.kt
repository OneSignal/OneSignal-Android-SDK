package com.onesignal.core.internal.features

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeatureFlagTests : FunSpec({
    test("feature flag remote keys are lowercase with underscores") {
        val keyPattern = Regex("^[a-z0-9_]+$")

        FeatureFlag.entries.forEach { feature ->
            keyPattern.matches(feature.key) shouldBe true
        }
    }

    test("SDK_BACKGROUND_THREADING uses the expected remote key") {
        FeatureFlag.SDK_BACKGROUND_THREADING.key shouldBe "sdk_background_threading"
    }

    test("SDK_IDENTITY_VERIFICATION uses the expected remote key and IMMEDIATE activation") {
        FeatureFlag.SDK_IDENTITY_VERIFICATION.key shouldBe "sdk_identity_verification"
        FeatureFlag.SDK_IDENTITY_VERIFICATION.activationMode shouldBe FeatureActivationMode.IMMEDIATE
    }
})
