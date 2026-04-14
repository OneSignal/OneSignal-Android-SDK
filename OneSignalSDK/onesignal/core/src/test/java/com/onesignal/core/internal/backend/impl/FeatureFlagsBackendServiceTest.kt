package com.onesignal.core.internal.backend.impl

import com.onesignal.common.OneSignalUtils
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeatureFlagsBackendServiceTest : FunSpec({
    test("buildFeatureFlagsGetPath matches Turbine /apps/:app_id/sdk/features/:platform/:sdk_version") {
        FeatureFlagsBackendService.buildFeatureFlagsGetPath(
            appId = "14719551-23f1-4d20-8dab-81496ffca5ea",
            platform = FeatureFlagsBackendService.TURBINE_FEATURES_PLATFORM_ANDROID,
            sdkVersion = "050801",
        ) shouldBe "apps/14719551-23f1-4d20-8dab-81496ffca5ea/sdk/features/android/050801"
    }

    test("buildFeatureFlagsGetPath encodes prerelease sdk version label") {
        FeatureFlagsBackendService.buildFeatureFlagsGetPath(
            appId = "14719551-23f1-4d20-8dab-81496ffca5ea",
            platform = FeatureFlagsBackendService.TURBINE_FEATURES_PLATFORM_ANDROID,
            sdkVersion = "050801-beta",
        ) shouldBe "apps/14719551-23f1-4d20-8dab-81496ffca5ea/sdk/features/android/050801-beta"
    }

    test("isValidFeaturesSdkVersionLabel accepts only 6-digit labels with optional -suffix") {
        val valid =
            listOf(
                "050801",
                "050801-beta",
                "050801-beta1",
                "010203-rc.2",
                "000000",
            )
        val invalid =
            listOf(
                "5.8.1",
                "05080",
                "0508010",
                "",
                "050801-",
                "050801/",
                "v050801",
            )
        valid.forEach { FeatureFlagsBackendService.isValidFeaturesSdkVersionLabel(it) shouldBe true }
        invalid.forEach { FeatureFlagsBackendService.isValidFeaturesSdkVersionLabel(it) shouldBe false }
    }

    test("OneSignalUtils.sdkVersion from BuildConfig matches Turbine label rules") {
        FeatureFlagsBackendService.isValidFeaturesSdkVersionLabel(OneSignalUtils.sdkVersion) shouldBe true
    }
})
