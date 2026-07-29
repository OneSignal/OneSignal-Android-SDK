package com.onesignal.core.activities

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.R
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

@RobolectricTest
class PermissionsActivityThemeTests : FunSpec({
    test("PermissionsActivity uses the transparent system bar theme") {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo =
            context.packageManager.getActivityInfo(
                ComponentName(context, PermissionsActivity::class.java),
                0,
            )

        activityInfo.themeResource shouldBe R.style.OneSignal_Theme_Translucent

        val attributes =
            ContextThemeWrapper(context, activityInfo.themeResource).obtainStyledAttributes(
                intArrayOf(
                    android.R.attr.windowBackground,
                    android.R.attr.windowDrawsSystemBarBackgrounds,
                    android.R.attr.statusBarColor,
                    android.R.attr.navigationBarColor,
                ),
            )

        try {
            (attributes.getDrawable(0) as ColorDrawable).color shouldBe Color.TRANSPARENT
            attributes.getBoolean(1, false) shouldBe true
            attributes.getColor(2, Color.BLACK) shouldBe Color.TRANSPARENT
            attributes.getColor(3, Color.BLACK) shouldBe Color.TRANSPARENT
        } finally {
            attributes.recycle()
        }
    }
})
