package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import android.webkit.WebView
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.ViewUtils
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.display.impl.InAppMessageView
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import io.kotest.core.spec.style.FunSpec
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
        val view = messageView(mockk(relaxed = true))
        val controller = mockk<InAppMessageView.InAppMessageViewListener>(relaxed = true)
        view.setMessageController(controller)
        mockkObject(ViewUtils)
        every { ViewUtils.getWindowHeight(any()) } throws IllegalStateException("boom")

        try {
            view.showInAppMessageView(activity)
        } finally {
            unmockkObject(ViewUtils)
        }

        verify(exactly = 1) { controller.onMessageWasDismissed() }
    }
})

private fun messageView(webView: WebView): InAppMessageView {
    val content = InAppMessageContent(JSONObject())
    content.displayLocation = WebViewManager.Position.CENTER_MODAL
    return InAppMessageView(webView, content, false, true)
}
