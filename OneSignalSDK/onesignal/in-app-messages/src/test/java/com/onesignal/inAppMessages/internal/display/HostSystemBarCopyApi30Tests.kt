package com.onesignal.inAppMessages.internal.display

import android.graphics.Insets
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.inAppMessages.internal.display.impl.copyHostSystemBarVisibility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.robolectric.annotation.Config

private val STATUS_BARS = WindowInsets.Type.statusBars()
private val NAVIGATION_BARS = WindowInsets.Type.navigationBars()
private const val LIGHT_BARS_MASK =
    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS

@Config(
    packageName = "com.onesignal.example",
    sdk = [30],
)
@RobolectricTest
class HostSystemBarCopyApi30Tests : FunSpec({
    test("api 30 copy hides the status bar the host hides and shows the nav bar it shows") {
        val hostController = hostController(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        val host = hostDecor(insets(statusVisible = false, navVisible = true), hostController)
        val popupController = mockk<WindowInsetsController>(relaxed = true)

        copyHostSystemBarVisibility(host, popupDecor(popupController))

        verify(exactly = 1) { popupController.hide(STATUS_BARS) }
        verify(exactly = 1) { popupController.show(NAVIGATION_BARS) }
        verify(exactly = 0) { popupController.show(STATUS_BARS) }
        verify(exactly = 0) { popupController.hide(NAVIGATION_BARS) }
    }

    test("api 30 copy shows the status bar the host shows and hides the nav bar it hides") {
        val hostController = hostController(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        val host = hostDecor(insets(statusVisible = true, navVisible = false), hostController)
        val popupController = mockk<WindowInsetsController>(relaxed = true)

        copyHostSystemBarVisibility(host, popupDecor(popupController))

        verify(exactly = 1) { popupController.show(STATUS_BARS) }
        verify(exactly = 1) { popupController.hide(NAVIGATION_BARS) }
        verify(exactly = 0) { popupController.hide(STATUS_BARS) }
        verify(exactly = 0) { popupController.show(NAVIGATION_BARS) }
    }

    test("api 30 copy takes the host bar behavior and light bar appearance") {
        val hostController = hostController(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
        every { hostController.systemBarsAppearance } returns WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        val host = hostDecor(insets(statusVisible = false, navVisible = false), hostController)
        val popupController = mockk<WindowInsetsController>(relaxed = true)
        val seenAppearance = mutableListOf<Pair<Int, Int>>()
        every { popupController.setSystemBarsAppearance(any(), any()) } answers {
            seenAppearance.add(firstArg<Int>() to secondArg())
        }

        copyHostSystemBarVisibility(host, popupDecor(popupController))

        verify(exactly = 1) {
            popupController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        seenAppearance shouldBe
            listOf(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS to LIGHT_BARS_MASK)
    }

    test("api 30 copy still mirrors visibility when the host has no insets controller") {
        val host = hostDecor(insets(statusVisible = false, navVisible = true), hostController = null)
        val popupController = mockk<WindowInsetsController>(relaxed = true)
        val seenAppearance = mutableListOf<Pair<Int, Int>>()
        every { popupController.setSystemBarsAppearance(any(), any()) } answers {
            seenAppearance.add(firstArg<Int>() to secondArg())
        }

        copyHostSystemBarVisibility(host, popupDecor(popupController))

        verify(exactly = 1) { popupController.hide(STATUS_BARS) }
        verify(exactly = 0) { popupController.systemBarsBehavior = any() }
        seenAppearance shouldBe emptyList()
    }

    test("api 30 copy leaves the popup alone until the host has insets") {
        val host = hostDecor(insets = null, hostController(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE))
        val popupController = mockk<WindowInsetsController>(relaxed = true)

        copyHostSystemBarVisibility(host, popupDecor(popupController))

        verify(exactly = 0) { popupController.hide(any()) }
        verify(exactly = 0) { popupController.show(any()) }
        verify(exactly = 0) { popupController.systemBarsBehavior = any() }
    }
})

private fun insets(
    statusVisible: Boolean,
    navVisible: Boolean,
): WindowInsets =
    WindowInsets.Builder()
        .setInsets(STATUS_BARS, Insets.of(0, 40, 0, 0))
        .setInsets(NAVIGATION_BARS, Insets.of(0, 0, 0, 48))
        .setVisible(STATUS_BARS, statusVisible)
        .setVisible(NAVIGATION_BARS, navVisible)
        .build()

private fun hostController(behavior: Int): WindowInsetsController {
    val controller = mockk<WindowInsetsController>(relaxed = true)
    every { controller.systemBarsBehavior } returns behavior
    return controller
}

private fun hostDecor(
    insets: WindowInsets?,
    hostController: WindowInsetsController?,
): View {
    val host = mockk<View>(relaxed = true)
    every { host.rootWindowInsets } returns insets
    every { host.windowInsetsController } returns hostController
    return host
}

private fun popupDecor(controller: WindowInsetsController): View {
    val popup = mockk<View>(relaxed = true)
    every { popup.windowInsetsController } returns controller
    return popup
}
