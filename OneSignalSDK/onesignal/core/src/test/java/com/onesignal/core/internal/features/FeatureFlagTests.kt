package com.onesignal.core.internal.features

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeatureFlagTests : FunSpec({
    test("feature flags use SDK_050800 style key naming") {
        val keyPattern = Regex("^SDK_[0-9]{6}_[A-Z0-9_]+$")

        FeatureFlag.entries.forEach { feature ->
            keyPattern.matches(feature.key) shouldBe true
        }
    }

    test("BACKGROUND_THREADING uses the expected canonical key") {
        FeatureFlag.SDK_050800_BACKGROUND_THREADING.key shouldBe "SDK_050800_BACKGROUND_THREADING"
    }
})
