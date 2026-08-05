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
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    test("early dismiss with null messageView destroys WebView and fires lifecycle dismissed") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val manager = createManager(activity, applicationService, lifecycle, message)

        setWebViewField(manager, OSWebView(activity))

        var dismissed = false
        manager.onDismissed = { dismissed = true }

        runBlocking {
            manager.dismissAndAwaitNextMessage()
        }

        getWebViewField(manager).shouldBeNull()
        dismissed shouldBe true
        verify(exactly = 1) { lifecycle.messageWillDismiss(message) }
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
        verify(exactly = 1) { applicationService.removeActivityLifecycleHandler(manager) }
    }

    test("cleanup is idempotent across repeated dismiss calls") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val manager = createManager(activity, applicationService, lifecycle, message)

        setWebViewField(manager, OSWebView(activity))

        var dismissCount = 0
        manager.onDismissed = { dismissCount++ }

        runBlocking {
            manager.dismissAndAwaitNextMessage()
            manager.dismissAndAwaitNextMessage()
        }

        getWebViewField(manager).shouldBeNull()
        dismissCount shouldBe 1
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
    }

    test("concurrent dismiss only completes cleanup and lifecycle once") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val manager = createManager(activity, applicationService, lifecycle, message)

        setWebViewField(manager, OSWebView(activity))

        var dismissCount = 0
        manager.onDismissed = { dismissCount++ }

        runBlocking {
            (1..8).map {
                async { manager.dismissAndAwaitNextMessage() }
            }.awaitAll()
        }

        getWebViewField(manager).shouldBeNull()
        dismissCount shouldBe 1
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
        verify(atMost = 1) { applicationService.removeActivityLifecycleHandler(manager) }
    }

    test("setupWebView after dismiss does not retain a WebView") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        coEvery { applicationService.waitUntilActivityReady() } returns true
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val manager =
            createManager(
                activity,
                applicationService,
                lifecycle,
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        runBlocking {
            manager.dismissAndAwaitNextMessage()
            manager.setupWebView(activity, "", false)
        }

        getWebViewField(manager).shouldBeNull()
    }

    test("createNewInAppMessageView after dismiss is a no-op") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        val manager =
            createManager(
                activity,
                applicationService,
                mockk(relaxed = true),
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        setWebViewField(manager, OSWebView(activity))

        runBlocking {
            manager.dismissAndAwaitNextMessage()
        }

        manager.createNewInAppMessageView(false)

        getMessageViewField(manager).shouldBeNull()
        verify(exactly = 0) { applicationService.addActivityLifecycleHandler(manager) }
    }
})

private fun createManager(
    activity: Activity,
    applicationService: IApplicationService,
    lifecycle: IInAppLifecycleService,
    message: InAppMessage,
    promptFactory: IInAppMessagePromptFactory = mockk(relaxed = true),
): WebViewManager {
    return WebViewManager(
        message,
        activity,
        InAppMessageContent(JSONObject().put("html", "<html></html>")),
        lifecycle,
        applicationService,
        promptFactory,
    )
}

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

private fun getMessageViewField(manager: WebViewManager): Any? {
    val field = WebViewManager::class.java.getDeclaredField("messageView")
    field.isAccessible = true
    return field.get(manager)
}
