package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.display.impl.OSWebView
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [34],
)
@RobolectricTest
class WebViewManagerDismissCleanupTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("dismissAndAwaitNextMessage destroys WebView and invokes onDismissed when messageView is null") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val promptFactory = mockk<IInAppMessagePromptFactory>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val content = InAppMessageContent(JSONObject().put("html", "<html></html>"))

        val manager =
            WebViewManager(
                message,
                activity,
                content,
                lifecycle,
                applicationService,
                promptFactory,
            )

        val webView = OSWebView(activity)
        setWebViewField(manager, webView)

        var dismissed = false
        manager.onDismissed = { dismissed = true }

        runBlocking {
            manager.dismissAndAwaitNextMessage()
        }

        getWebViewField(manager).shouldBeNull()
        dismissed shouldBe true
        verify(exactly = 1) { applicationService.removeActivityLifecycleHandler(manager) }
    }

    test("cleanup is idempotent across repeated dismiss calls") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        val manager =
            WebViewManager(
                InAppMessage("test-iam", MockHelper.time(1)),
                activity,
                InAppMessageContent(JSONObject().put("html", "<html></html>")),
                mockk(relaxed = true),
                applicationService,
                mockk(relaxed = true),
            )

        setWebViewField(manager, OSWebView(activity))

        var dismissCount = 0
        manager.onDismissed = { dismissCount++ }

        runBlocking {
            manager.dismissAndAwaitNextMessage()
            manager.dismissAndAwaitNextMessage()
        }

        getWebViewField(manager).shouldBeNull()
        dismissCount shouldBe 1
    }
})

private fun setWebViewField(
    manager: WebViewManager,
    value: OSWebView?,
) {
    val field = WebViewManager::class.java.getDeclaredField("webView")
    field.isAccessible = true
    field.set(manager, value)
}

private fun getWebViewField(manager: WebViewManager): Any? {
    val field = WebViewManager::class.java.getDeclaredField("webView")
    field.isAccessible = true
    return field.get(manager)
}
