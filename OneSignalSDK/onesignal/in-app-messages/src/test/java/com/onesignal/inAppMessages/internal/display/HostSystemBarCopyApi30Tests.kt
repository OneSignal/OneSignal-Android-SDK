package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import android.graphics.Insets
import android.view.WindowInsets
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.inAppMessages.internal.display.impl.copyHostSystemBarVisibility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [30],
)
@RobolectricTest
class HostSystemBarCopyApi30Tests : FunSpec({
    test("api 30 copy applies the host bar visibility to the popup") {
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        val popup = Robolectric.buildActivity(Activity::class.java).setup().get()
        val insets =
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 40, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setVisible(WindowInsets.Type.statusBars(), false)
                .setVisible(WindowInsets.Type.navigationBars(), true)
                .build()
        host.window.decorView.dispatchApplyWindowInsets(insets)

        copyHostSystemBarVisibility(host.window.decorView, popup.window.decorView)

        popup.window.decorView.windowInsetsController.shouldNotBeNull()
    }

    test("api 30 copy hides a visible status bar and shows a hidden nav bar") {
        val host = Robolectric.buildActivity(Activity::class.java).setup().get()
        val popup = Robolectric.buildActivity(Activity::class.java).setup().get()
        val insets =
            WindowInsets.Builder()
                .setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 40, 0, 0))
                .setInsets(WindowInsets.Type.navigationBars(), Insets.of(0, 0, 0, 48))
                .setVisible(WindowInsets.Type.statusBars(), true)
                .setVisible(WindowInsets.Type.navigationBars(), false)
                .build()
        host.window.decorView.dispatchApplyWindowInsets(insets)

        copyHostSystemBarVisibility(host.window.decorView, popup.window.decorView)

        popup.window.decorView.windowInsetsController.shouldNotBeNull()
    }
})
