package com.onesignal.common

import android.content.res.Resources
import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
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
        val reported = SystemBarInsets.cutoutAndStatusBarInsetsDp(insets)

        reported[0] shouldBe (24 / density).roundToInt()
        reported[1] shouldBe (48 / density).roundToInt()
        reported[2] shouldBe 0
        reported[3] shouldBe 0
    }

    test("a hidden navigation bar does not keep a bottom or side inset") {
        SystemBarInsets.pxInsetsHidingSystemBars(
            intArrayOf(24, 48, 0, 0),
            statusHidden = false,
            navigationHidden = true,
        ) shouldBe intArrayOf(24, 0, 0, 0)

        SystemBarInsets.pxInsetsHidingSystemBars(
            intArrayOf(24, 0, 48, 0),
            statusHidden = false,
            navigationHidden = true,
        ) shouldBe intArrayOf(24, 0, 0, 0)
    }

    test("window flags without fullscreen leave the visibility unchanged") {
        SystemBarInsets.systemUiVisibilityForInsets(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION, 0) shouldBe
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    test("a nav bar on the left drops the left inset") {
        SystemBarInsets.pxInsetsHidingSystemBars(
            intArrayOf(10, 20, 5, 40),
            statusHidden = false,
            navigationHidden = true,
        ) shouldBe intArrayOf(10, 0, 5, 0)
    }

    test("a fullscreen window flag counts as a hidden status bar") {
        val flags =
            SystemBarInsets.systemUiVisibilityForInsets(
                0,
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            )
        (flags and View.SYSTEM_UI_FLAG_FULLSCREEN) shouldBe View.SYSTEM_UI_FLAG_FULLSCREEN
    }

    test("a hidden status bar does not keep a top inset") {
        SystemBarInsets.pxInsetsHidingSystemBars(
            intArrayOf(24, 48, 0, 0),
            statusHidden = true,
            navigationHidden = false,
        ) shouldBe intArrayOf(0, 48, 0, 0)
    }
})
