package com.onesignal.core.internal.backend.impl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TurbineSdkFeatureFlagsPathTest : FunSpec({
    test("percentEncodePathSegmentUtf8 leaves unreserved characters unchanged") {
        TurbineSdkFeatureFlagsPath.percentEncodePathSegmentUtf8("android") shouldBe "android"
        TurbineSdkFeatureFlagsPath.percentEncodePathSegmentUtf8("050801-beta") shouldBe "050801-beta"
        TurbineSdkFeatureFlagsPath.percentEncodePathSegmentUtf8("aZ09-._~") shouldBe "aZ09-._~"
    }

    test("percentEncodePathSegmentUtf8 encodes reserved and space as percent-hex") {
        TurbineSdkFeatureFlagsPath.percentEncodePathSegmentUtf8("a/b") shouldBe "a%2Fb"
        TurbineSdkFeatureFlagsPath.percentEncodePathSegmentUtf8("a b") shouldBe "a%20b"
        TurbineSdkFeatureFlagsPath.percentEncodePathSegmentUtf8("café") shouldBe "caf%C3%A9"
    }

    test("buildGetPath matches expected Turbine relative path") {
        TurbineSdkFeatureFlagsPath.buildGetPath(
            appId = "14719551-23f1-4d20-8dab-81496ffca5ea",
            platform = "android",
            sdkVersion = "050801-beta",
        ) shouldBe "apps/14719551-23f1-4d20-8dab-81496ffca5ea/sdk/features/android/050801-beta"
    }
})
