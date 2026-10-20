package com.onesignal.inAppMessages.internal.display

import android.view.View
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.display.impl.copyHostSystemBarVisibility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [29],
)
@RobolectricTest
class HostSystemBarCopyTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("popup copies host hide flags and does not add them on its own") {
        val context = RuntimeEnvironment.getApplication()
        val host = View(context)
        val popup = View(context)
        @Suppress("DEPRECATION")
        host.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE

        copyHostSystemBarVisibility(host, popup)

        @Suppress("DEPRECATION")
        popup.systemUiVisibility shouldBe host.systemUiVisibility

        val plainHost = View(context)
        val plainPopup = View(context)
        copyHostSystemBarVisibility(plainHost, plainPopup)

        @Suppress("DEPRECATION")
        (plainPopup.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) shouldBe 0
    }

    test("pre-R copy keeps the popup layout flags and drops the host layout flags") {
        val context = RuntimeEnvironment.getApplication()
        val host = View(context)
        val popup = View(context)
        @Suppress("DEPRECATION")
        host.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        @Suppress("DEPRECATION")
        popup.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        copyHostSystemBarVisibility(host, popup)

        @Suppress("DEPRECATION")
        (popup.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) shouldBe
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        @Suppress("DEPRECATION")
        (popup.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) shouldBe
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        @Suppress("DEPRECATION")
        (popup.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) shouldBe 0
    }

    test("copying onto the host decor itself does nothing") {
        val host = View(RuntimeEnvironment.getApplication())
        @Suppress("DEPRECATION")
        host.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        copyHostSystemBarVisibility(host, host)

        @Suppress("DEPRECATION")
        host.systemUiVisibility shouldBe View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
})
