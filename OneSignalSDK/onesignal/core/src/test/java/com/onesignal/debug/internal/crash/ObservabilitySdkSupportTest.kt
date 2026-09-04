package com.onesignal.debug.internal.crash

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class ObservabilitySdkSupportTest : FunSpec({

    afterEach {
        ObservabilitySdkSupport.reset()
    }

    test("isSupported is true on SDK >= 23") {
        ObservabilitySdkSupport.reset()
        ObservabilitySdkSupport.isSupported shouldBe true
    }

    test("isSupported can be overridden to false for testing") {
        ObservabilitySdkSupport.isSupported = false
        ObservabilitySdkSupport.isSupported shouldBe false
    }

    test("reset restores runtime-detected value") {
        ObservabilitySdkSupport.isSupported = false
        ObservabilitySdkSupport.isSupported shouldBe false

        ObservabilitySdkSupport.reset()
        ObservabilitySdkSupport.isSupported shouldBe true
    }

    test("MIN_SDK_VERSION is 23") {
        ObservabilitySdkSupport.MIN_SDK_VERSION shouldBe 23
    }
})

@RobolectricTest
@Config(sdk = [23])
class ObservabilitySdkSupportApi23Test : FunSpec({
    afterEach {
        ObservabilitySdkSupport.reset()
    }

    test("isSupported is true on API 23") {
        ObservabilitySdkSupport.reset()
        ObservabilitySdkSupport.isSupported shouldBe true
    }
})

@RobolectricTest
@Config(sdk = [22])
class ObservabilitySdkSupportApi22Test : FunSpec({
    afterEach {
        ObservabilitySdkSupport.reset()
    }

    test("isSupported is false below API 23") {
        ObservabilitySdkSupport.reset()
        ObservabilitySdkSupport.isSupported shouldBe false
    }
})
