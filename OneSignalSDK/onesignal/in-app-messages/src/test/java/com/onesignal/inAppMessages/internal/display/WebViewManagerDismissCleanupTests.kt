package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.display.impl.InAppMessageView
import com.onesignal.inAppMessages.internal.display.impl.OSWebView
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(
    packageName = "com.onesignal.example",
    sdk = [34],
)
@RobolectricTest
class WebViewManagerDismissCleanupTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        // After Robolectric Looper init; setupWebView must run on a Main looper thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterAny {
        Dispatchers.resetMain()
    }

    test("early dismiss with null messageView destroys WebView and fires lifecycle dismissed") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
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
        val applicationService = mockApplicationService(activity)
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

    test("concurrent dismiss on Default dispatcher only completes cleanup once") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val manager = createManager(activity, applicationService, lifecycle, message)

        setWebViewField(manager, OSWebView(activity))

        var dismissCount = 0
        manager.onDismissed = { dismissCount++ }

        runBlocking {
            withContext(Dispatchers.Default) {
                (1..8).map {
                    async(Dispatchers.Default) { manager.dismissAndAwaitNextMessage() }
                }.awaitAll()
            }
        }

        getWebViewField(manager).shouldBeNull()
        dismissCount shouldBe 1
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
    }

    test("dismiss during waitUntilActivityReady destroys in-flight WebView") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val ready = CompletableDeferred<Boolean>()
        val enteredWait = CompletableDeferred<Unit>()
        coEvery { applicationService.waitUntilActivityReady() } coAnswers {
            enteredWait.complete(Unit)
            ready.await()
        }
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val manager =
            createManager(
                activity,
                applicationService,
                lifecycle,
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        runBlocking {
            // Match production: WebView setup runs on Main; dismiss races from Default.
            val setupJob =
                async(Dispatchers.Main) {
                    manager.setupWebView(activity, "", false)
                }

            withTimeout(2_000) { enteredWait.await() }
            async(Dispatchers.Default) {
                manager.dismissAndAwaitNextMessage()
            }.await()
            ready.complete(true)
            setupJob.await()
        }

        getWebViewField(manager).shouldBeNull()
        coVerify(exactly = 1) { applicationService.waitUntilActivityReady() }
        verify(exactly = 1) { lifecycle.messageWasDismissed(any()) }
    }

    test("createNewInAppMessageView after dismiss is a no-op") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
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

    test("message view dismiss path destroys WebView and notifies lifecycle once") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val content =
            InAppMessageContent(
                JSONObject()
                    .put("html", "<html></html>")
                    .put("display_duration", 0),
            )
        content.displayLocation = WebViewManager.Position.CENTER_MODAL
        content.pageHeight = 100

        val manager =
            WebViewManager(
                message,
                activity,
                content,
                lifecycle,
                applicationService,
                mockk(relaxed = true),
            )

        setWebViewField(manager, OSWebView(activity))
        manager.createNewInAppMessageView(false)
        getMessageViewField(manager).shouldNotBeNull()

        var dismissed = false
        manager.onDismissed = { dismissed = true }

        val controller = getMessageController(getMessageViewField(manager)!!)
        controller.shouldNotBeNull()
        controller!!.onMessageWasDismissed()

        getWebViewField(manager).shouldBeNull()
        getMessageViewField(manager).shouldBeNull()
        dismissed shouldBe true
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
        verify(exactly = 1) { applicationService.addActivityLifecycleHandler(manager) }
        verify(atLeast = 1) { applicationService.removeActivityLifecycleHandler(manager) }
    }

    test("setupWebView after dismiss does not retain a WebView") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        coEvery { applicationService.waitUntilActivityReady() } returns true
        val manager =
            createManager(
                activity,
                applicationService,
                mockk(relaxed = true),
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        runBlocking {
            manager.dismissAndAwaitNextMessage()
            manager.setupWebView(activity, "", false)
        }

        getWebViewField(manager).shouldBeNull()
    }

    test("successful setupWebView retains WebView until dismiss") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
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
            manager.setupWebView(activity, "", false)
        }

        getWebViewField(manager).shouldNotBeNull()
        verify(exactly = 1) { lifecycle.messageWillDisplay(any()) }

        runBlocking {
            manager.dismissAndAwaitNextMessage()
        }

        getWebViewField(manager).shouldBeNull()
    }

    test("render complete after dismiss is ignored") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val manager =
            createManager(
                activity,
                applicationService,
                mockk(relaxed = true),
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        runBlocking { manager.dismissAndAwaitNextMessage() }

        val jsInterface = newJsInterface(manager)
        jsInterface.postMessage(
            """{"type":"rendering_complete","displayLocation":"CENTER_MODAL","pageMetaData":{"rect":{"height":120}},"dragToDismissDisabled":false}""",
        )

        getMessageViewField(manager).shouldBeNull()
        verify(exactly = 0) { applicationService.addActivityLifecycleHandler(manager) }
    }

    test("showMessageView is a no-op after dismissed flag is set") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val activityB = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val content =
            InAppMessageContent(
                JSONObject()
                    .put("html", "<html></html>")
                    .put("display_duration", 0)
                    .put("styles", JSONObject().put("remove_height_margin", true)),
            )
        content.displayLocation = WebViewManager.Position.FULL_SCREEN
        content.pageHeight = 200

        val manager =
            WebViewManager(
                InAppMessage("test-iam", MockHelper.time(1)),
                activity,
                content,
                lifecycle,
                applicationService,
                mockk(relaxed = true),
            )
        setWebViewField(manager, OSWebView(activity))
        manager.createNewInAppMessageView(false)

        // Mark dismissed without cleaning messageView, then exercise activity transition.
        setDismissedField(manager, true)
        manager.onActivityAvailable(activityB)

        getMessageViewField(manager).shouldNotBeNull()
    }

    test("full screen setup applies immersive flags and still cleans up on dismiss") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val manager =
            createManager(
                activity,
                applicationService,
                mockk(relaxed = true),
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        runBlocking {
            manager.setupWebView(activity, "", true)
        }

        getWebViewField(manager).shouldNotBeNull()

        runBlocking { manager.dismissAndAwaitNextMessage() }

        getWebViewField(manager).shouldBeNull()
    }

    test("page metadata callback ignores empty payloads and bad JSON") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val manager =
            createManager(
                activity,
                applicationService,
                mockk(relaxed = true),
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        // "null" is what evaluateJavascript reports when the JS function is undefined.
        evaluatePageMetaData(manager, null)
        evaluatePageMetaData(manager, "")
        evaluatePageMetaData(manager, "null")
        evaluatePageMetaData(manager, "{not-json")

        getMessageViewField(manager).shouldBeNull()
    }

    test("page metadata callback with a valid height reaches showMessageView") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val manager =
            createManager(
                activity,
                applicationService,
                lifecycle,
                InAppMessage("test-iam", MockHelper.time(1)),
            )

        setWebViewField(manager, OSWebView(activity))
        evaluatePageMetaData(manager, """{"rect":{"height":140}}""")

        // showMessageView is dispatched on IO; it must bail out safely with no messageView.
        runBlocking { withTimeout(2_000) { while (getMessageViewField(manager) != null) delay(10) } }

        getMessageViewField(manager).shouldBeNull()
    }

    test("dismiss completes even when destroying the WebView throws") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val manager = createManager(activity, applicationService, lifecycle, message)

        val failingWebView = mockk<OSWebView>(relaxed = true)
        every { failingWebView.stopLoading() } throws RuntimeException("WebView already destroyed")
        setWebViewField(manager, failingWebView)

        runBlocking { manager.dismissAndAwaitNextMessage() }

        getWebViewField(manager).shouldBeNull()
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
    }

    test("finishDismiss detaches the message view so a racing show cannot orphan it") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val content =
            InAppMessageContent(
                JSONObject()
                    .put("html", "<html></html>")
                    .put("display_duration", 0),
            )
        content.displayLocation = WebViewManager.Position.CENTER_MODAL
        content.pageHeight = 100

        val manager =
            WebViewManager(
                message,
                activity,
                content,
                mockk(relaxed = true),
                applicationService,
                mockk(relaxed = true),
            )
        setWebViewField(manager, OSWebView(activity))
        manager.createNewInAppMessageView(false)

        val view = getMessageViewField(manager)
        view.shouldNotBeNull()
        setWebViewOnMessageView(view, OSWebView(activity))

        // System dismiss (OSPopupWindow.PopupWindowListener.onDismiss) calls straight into
        // onMessageWasDismissed without running removeAllViews first, so finishDismiss is the
        // only thing that can tear the view down before dropping the reference to it.
        getMessageController(view)!!.onMessageWasDismissed()

        getMessageViewField(manager).shouldBeNull()
        getWebViewOnMessageView(view).shouldBeNull()
    }

    test("message lifecycle callbacks and finishDismiss are single-flight") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockApplicationService(activity)
        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val message = InAppMessage("test-iam", MockHelper.time(1))
        val content =
            InAppMessageContent(
                JSONObject()
                    .put("html", "<html></html>")
                    .put("display_duration", 0),
            )
        content.displayLocation = WebViewManager.Position.CENTER_MODAL
        content.pageHeight = 100

        val manager =
            WebViewManager(
                message,
                activity,
                content,
                lifecycle,
                applicationService,
                mockk(relaxed = true),
            )
        setWebViewField(manager, OSWebView(activity))
        manager.createNewInAppMessageView(false)

        val controller = getMessageController(getMessageViewField(manager)!!)!!
        controller.onMessageWasDisplayed()
        controller.onMessageWillDismiss()

        runBlocking {
            withContext(Dispatchers.Default) {
                listOf(
                    async(Dispatchers.Default) { controller.onMessageWasDismissed() },
                    async(Dispatchers.Default) { manager.dismissAndAwaitNextMessage() },
                ).awaitAll()
            }
        }

        verify(exactly = 1) { lifecycle.messageWasDisplayed(message) }
        verify(atLeast = 1) { lifecycle.messageWillDismiss(message) }
        verify(exactly = 1) { lifecycle.messageWasDismissed(message) }
        getWebViewField(manager).shouldBeNull()
    }
})

private fun mockApplicationService(activity: Activity): IApplicationService {
    val applicationService = mockk<IApplicationService>(relaxed = true)
    every { applicationService.current } returns activity
    every { applicationService.appContext } returns activity.applicationContext
    every { applicationService.addActivityLifecycleHandler(any()) } just runs
    every { applicationService.removeActivityLifecycleHandler(any()) } just runs
    coEvery { applicationService.waitUntilActivityReady() } returns true
    return applicationService
}

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

private fun getMessageViewField(manager: WebViewManager): InAppMessageView? {
    val field = WebViewManager::class.java.getDeclaredField("messageView")
    field.isAccessible = true
    return field.get(manager) as InAppMessageView?
}

private fun getMessageController(messageView: InAppMessageView): InAppMessageView.InAppMessageViewListener? {
    val field = InAppMessageView::class.java.getDeclaredField("messageController")
    field.isAccessible = true
    return field.get(messageView) as InAppMessageView.InAppMessageViewListener?
}

private fun setDismissedField(
    manager: WebViewManager,
    value: Boolean,
) {
    val field = WebViewManager::class.java.getDeclaredField("dismissed")
    field.isAccessible = true
    field.setBoolean(manager, value)
}

private fun setWebViewOnMessageView(
    messageView: InAppMessageView,
    webView: OSWebView,
) {
    val field = InAppMessageView::class.java.getDeclaredField("webView")
    field.isAccessible = true
    field.set(messageView, webView)
}

private fun getWebViewOnMessageView(messageView: InAppMessageView): Any? {
    val field = InAppMessageView::class.java.getDeclaredField("webView")
    field.isAccessible = true
    return field.get(messageView)
}

private fun evaluatePageMetaData(
    manager: WebViewManager,
    value: String?,
) {
    val method =
        WebViewManager::class.java.getDeclaredMethod(
            "evaluatePageMetaDataForHeight",
            String::class.java,
        )
    method.isAccessible = true
    method.invoke(manager, value)
}

private fun newJsInterface(manager: WebViewManager): Any {
    val ifaceClass =
        WebViewManager::class.java.declaredClasses.first {
            it.simpleName == "OSJavaScriptInterface"
        }
    val ctor = ifaceClass.getDeclaredConstructor(WebViewManager::class.java)
    ctor.isAccessible = true
    val instance = ctor.newInstance(manager)
    return instance
}

private fun Any.postMessage(message: String) {
    val method = this.javaClass.getMethod("postMessage", String::class.java)
    method.invoke(this, message)
}
