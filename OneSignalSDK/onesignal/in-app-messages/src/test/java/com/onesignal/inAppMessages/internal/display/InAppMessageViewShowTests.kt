package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import android.graphics.Insets
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebView
import android.widget.PopupWindow
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.display.impl.InAppMessageView
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Suppress("DEPRECATION")
private const val HOST_HIDE_FLAGS = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

@Suppress("DEPRECATION")
private const val HOST_LAYOUT_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

@OptIn(ExperimentalCoroutinesApi::class)
@Config(
    packageName = "com.onesignal.example",
    sdk = [29],
)
@RobolectricTest
class InAppMessageViewShowTests : FunSpec({
    val logs = mutableListOf<String>()
    val logListener = ILogListener { logs.add(it.entry) }

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        logs.clear()
        Logging.addListener(logListener)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterAny {
        Logging.removeListener(logListener)
        Dispatchers.resetMain()
    }

    test("a center modal shows a focusable dialog popup with only the host hide flags") {
        val activity = readyActivity()
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = HOST_HIDE_FLAGS or HOST_LAYOUT_FLAGS
        val controller = mockk<InAppMessageView.InAppMessageViewListener>(relaxed = true)
        val view = messageView(WebView(activity), WebViewManager.Position.CENTER_MODAL, controller = controller)

        withTimeout(5_000) { view.showView(activity) }

        val popup = popupWindow(view).shouldNotBeNull()
        popup.isShowing shouldBe true
        popup.isFocusable shouldBe true
        val decor = popup.contentView.rootView
        (decor.layoutParams as WindowManager.LayoutParams).type shouldBe
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        @Suppress("DEPRECATION")
        (decor.systemUiVisibility and HOST_HIDE_FLAGS) shouldBe HOST_HIDE_FLAGS
        @Suppress("DEPRECATION")
        (decor.systemUiVisibility and HOST_LAYOUT_FLAGS) shouldBe 0
        verify(exactly = 0) { controller.onMessageWasDismissed() }

        view.removeAllViews()
    }

    test("a full bleed top banner shows a non focusable panel pinned to the top") {
        val activity = readyActivity()
        val view = messageView(WebView(activity), WebViewManager.Position.TOP_BANNER, fullBleed = true)

        withTimeout(5_000) { view.showView(activity) }

        val popup = popupWindow(view).shouldNotBeNull()
        popup.isFocusable shouldBe false
        popup.height shouldBe WindowManager.LayoutParams.WRAP_CONTENT
        val params = popup.contentView.rootView.layoutParams as WindowManager.LayoutParams
        params.type shouldBe WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
        params.gravity shouldBe (Gravity.CENTER_HORIZONTAL or Gravity.TOP)

        view.removeAllViews()
    }

    test("a bottom banner popup is pinned to the bottom") {
        val activity = readyActivity()
        val view = messageView(WebView(activity), WebViewManager.Position.BOTTOM_BANNER)

        withTimeout(5_000) { view.showView(activity) }

        val params = popupWindow(view).shouldNotBeNull().contentView.rootView.layoutParams as WindowManager.LayoutParams
        params.gravity shouldBe (Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM)

        view.removeAllViews()
    }

    test("a show without a web view logs and counts the message dismissed") {
        val activity = readyActivity()
        val controller = mockk<InAppMessageView.InAppMessageViewListener>(relaxed = true)
        val view = messageView(null, WebViewManager.Position.CENTER_MODAL, controller = controller)

        withTimeout(5_000) { view.showView(activity) }

        verify(exactly = 1) { controller.onMessageWasDismissed() }
        logs.map { it.substringAfter("] ") } shouldContain "In-app message not shown, WebView is missing."
        popupWindow(view).shouldBeNull()
    }

    test("a show with no main thread logs and counts the message dismissed") {
        val activity = readyActivity()
        val controller = mockk<InAppMessageView.InAppMessageViewListener>(relaxed = true)
        val view = messageView(WebView(activity), WebViewManager.Position.CENTER_MODAL, controller = controller)
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        try {
            coEvery { com.onesignal.common.threading.withMain(any<suspend CoroutineScope.() -> Boolean>()) } returns null

            withTimeout(5_000) { view.showView(activity) }
        } finally {
            unmockkStatic(threadUtilsPath)
        }

        verify(exactly = 1) { controller.onMessageWasDismissed() }
        logs.map { it.substringAfter("] ") } shouldContain
            "Could not show the in app message, the main thread is unavailable"
        popupWindow(view).shouldBeNull()
    }
})

private fun readyActivity(): Activity {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    @Suppress("DEPRECATION")
    activity.window.decorView.dispatchApplyWindowInsets(
        WindowInsets.Builder().setStableInsets(Insets.of(0, 1, 0, 0)).build(),
    )
    return activity
}

private fun messageView(
    webView: WebView?,
    position: WebViewManager.Position,
    fullBleed: Boolean = false,
    controller: InAppMessageView.InAppMessageViewListener? = null,
): InAppMessageView {
    val content = InAppMessageContent(JSONObject())
    content.displayLocation = position
    content.pageHeight = 100
    content.isFullBleed = fullBleed
    val view = InAppMessageView(webView, content, false, true)
    view.setMessageController(controller)
    return view
}

private fun popupWindow(view: InAppMessageView): PopupWindow? {
    val field = InAppMessageView::class.java.getDeclaredField("popupWindow")
    field.isAccessible = true
    return field.get(view) as PopupWindow?
}
