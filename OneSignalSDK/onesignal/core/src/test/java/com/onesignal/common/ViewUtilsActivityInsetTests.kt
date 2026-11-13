package com.onesignal.common

import android.app.Activity
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import android.view.DisplayCutout
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@Config(sdk = [21])
@RobolectricTest
class ViewUtilsLollipopInsetTests : FunSpec({
    test("lollipop uses the legacy inset path") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        ViewUtils.getCutoutAndStatusBarInsets(activity).size shouldBe 4
    }
})

@Config(sdk = [29])
@RobolectricTest
class ViewUtilsApi29InsetTests : FunSpec({
    test("a hidden status bar keeps a taller cutout") {
        @Suppress("DEPRECATION")
        val cutout = DisplayCutout(Rect(0, 30, 0, 0), emptyList())

        @Suppress("DEPRECATION")
        val insets =
            WindowInsets.Builder()
                .setStableInsets(Insets.of(0, 20, 0, 48))
                .setDisplayCutout(cutout)
                .build()

        val density = Resources.getSystem().displayMetrics.density
        val reported =
            SystemBarInsets.cutoutAndStatusBarInsetsDp(
                insets,
                View.SYSTEM_UI_FLAG_FULLSCREEN,
            )

        reported[0] shouldBe (30 / density).roundToInt()
        reported[1] shouldBe (48 / density).roundToInt()
    }

    test("activity insets follow the window when insets are present") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        @Suppress("DEPRECATION")
        val built =
            WindowInsets.Builder()
                .setStableInsets(Insets.of(0, 24, 0, 48))
                .build()
        activity.window.decorView.dispatchApplyWindowInsets(built)

        ViewUtils.getCutoutAndStatusBarInsets(activity).size shouldBe 4
    }
})
