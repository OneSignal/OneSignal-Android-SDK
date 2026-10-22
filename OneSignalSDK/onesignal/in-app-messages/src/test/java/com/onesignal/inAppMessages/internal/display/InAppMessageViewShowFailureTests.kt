package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import android.graphics.Insets
import android.view.WindowInsets
import android.webkit.WebView
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.ViewUtils
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.display.impl.InAppMessageView
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(sdk = [30])
@RobolectricTest
class InAppMessageViewShowFailureTests : FunSpec({
    test("a failure while showing is logged and the message is dismissed") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.window.decorView.dispatchApplyWindowInsets(
            WindowInsets.Builder().setInsets(WindowInsets.Type.statusBars(), Insets.of(0, 1, 0, 0)).build(),
        )
        val view = messageView(mockk(relaxed = true))
        val controller = mockk<InAppMessageView.InAppMessageViewListener>(relaxed = true)
        view.setMessageController(controller)
        val errors = mutableListOf<OneSignalLogEvent>()
        val listener = ILogListener { if (it.level == LogLevel.ERROR) errors.add(it) }
        Logging.addListener(listener)
        mockkObject(ViewUtils)
        every { ViewUtils.getWindowHeight(any()) } throws IllegalStateException("boom")

        try {
            view.showView(activity)
        } finally {
            unmockkObject(ViewUtils)
            Logging.removeListener(listener)
        }

        verify(exactly = 1) { controller.onMessageWasDismissed() }
        errors.size shouldBe 1
        errors[0].entry.contains("Could not show the in app message") shouldBe true
        errors[0].entry.contains("boom") shouldBe true
    }
})

private fun messageView(webView: WebView): InAppMessageView {
    val content = InAppMessageContent(JSONObject())
    content.displayLocation = WebViewManager.Position.CENTER_MODAL
    return InAppMessageView(webView, content, false, true)
}
