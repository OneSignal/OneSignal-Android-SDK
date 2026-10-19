package com.onesignal.common

import android.content.res.Resources
import android.graphics.Insets
import android.view.WindowInsets
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@Config(sdk = [34])
@RobolectricTest
class ViewUtilsInsetTests : FunSpec({
    test("system bar insets include the navigation bar and ignore the keyboard") {
        val insets =
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 400))
                .build()

        val density = Resources.getSystem().displayMetrics.density
        val reported = ViewUtils.cutoutAndStatusBarInsetsDp(insets)

        reported[0] shouldBe (24 / density).roundToInt()
        reported[1] shouldBe (48 / density).roundToInt()
        reported[2] shouldBe 0
        reported[3] shouldBe 0
    }

    test("a hidden navigation bar does not keep a bottom or side inset") {
        ViewUtils.pxInsetsHidingSystemBars(
            top = 24,
            bottom = 48,
            right = 0,
            left = 0,
            statusHidden = false,
            navigationHidden = true,
        ) shouldBe intArrayOf(24, 0, 0, 0)

        ViewUtils.pxInsetsHidingSystemBars(
            top = 24,
            bottom = 0,
            right = 48,
            left = 0,
            statusHidden = false,
            navigationHidden = true,
        ) shouldBe intArrayOf(24, 0, 0, 0)
    }

    test("a hidden status bar does not keep a top inset") {
        ViewUtils.pxInsetsHidingSystemBars(
            top = 24,
            bottom = 48,
            right = 0,
            left = 0,
            statusHidden = true,
            navigationHidden = false,
        ) shouldBe intArrayOf(0, 48, 0, 0)
    }
})
