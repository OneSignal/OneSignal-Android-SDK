package com.onesignal.debug.internal.crash

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelSdkSupportTest : FunSpec({

    afterEach {
        OtelSdkSupport.reset()
    }

    test("isSupported is true on SDK >= 26") {
        OtelSdkSupport.reset()
        OtelSdkSupport.isSupported shouldBe true
    }

    test("isSupported can be overridden to false for testing") {
        OtelSdkSupport.isSupported = false
        OtelSdkSupport.isSupported shouldBe false
    }

    test("reset restores runtime-detected value") {
        OtelSdkSupport.isSupported = false
        OtelSdkSupport.isSupported shouldBe false

        OtelSdkSupport.reset()
        OtelSdkSupport.isSupported shouldBe true
    }

    test("MIN_SDK_VERSION is 26") {
        OtelSdkSupport.MIN_SDK_VERSION shouldBe 26
    }
})
