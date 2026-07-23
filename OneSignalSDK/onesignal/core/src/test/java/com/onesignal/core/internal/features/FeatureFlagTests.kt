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

    test("SDK_IDENTITY_VERIFICATION uses the expected remote key and IMMEDIATE activation") {
        FeatureFlag.SDK_IDENTITY_VERIFICATION.key shouldBe "sdk_identity_verification"
        FeatureFlag.SDK_IDENTITY_VERIFICATION.activationMode shouldBe FeatureActivationMode.IMMEDIATE
    }

    test("SDK_CUSTOM_LOGGING uses the expected remote key and APP_STARTUP activation") {
        // APP_STARTUP so the observability module choice is latched per process and a remote
        // change only takes effect on the next app start.
        FeatureFlag.SDK_CUSTOM_LOGGING.key shouldBe "sdk_custom_logging"
        FeatureFlag.SDK_CUSTOM_LOGGING.activationMode shouldBe FeatureActivationMode.APP_STARTUP
    }
})
