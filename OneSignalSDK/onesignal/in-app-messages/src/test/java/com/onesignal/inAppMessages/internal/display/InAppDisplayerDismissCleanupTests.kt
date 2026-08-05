package com.onesignal.inAppMessages.internal.display

import android.app.Activity
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageContent
import com.onesignal.inAppMessages.internal.backend.GetIAMDataResponse
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.display.impl.InAppDisplayer
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.influence.IInfluenceManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(
    packageName = "com.onesignal.example",
    sdk = [34],
)
@RobolectricTest
class InAppDisplayerDismissCleanupTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        // Create after Robolectric has initialized Looper.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterAny {
        Dispatchers.resetMain()
    }

    test("dismissCurrentInAppMessage clears lastInstance via onDismissed callback") {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val applicationService = mockk<IApplicationService>(relaxed = true)
        every { applicationService.current } returns activity
        coEvery { applicationService.waitUntilActivityReady() } returns true
        every { applicationService.addActivityLifecycleHandler(any()) } just runs
        every { applicationService.removeActivityLifecycleHandler(any()) } just runs

        val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
        val backend = mockk<IInAppBackendService>()
        val content =
            InAppMessageContent(
                JSONObject()
                    .put("html", "<html></html>")
                    .put("display_duration", 0),
            )
        coEvery { backend.getIAMData(any(), any(), any()) } returns GetIAMDataResponse(content, false)

        val displayer =
            InAppDisplayer(
                applicationService,
                lifecycle,
                mockk<IInAppMessagePromptFactory>(relaxed = true),
                backend,
                mockk<IInfluenceManager>(relaxed = true),
                MockHelper.configModelStore(),
                MockHelper.languageContext(),
                MockHelper.time(1),
            )

        val message = InAppMessage("test-iam", MockHelper.time(1))

        runBlocking {
            displayer.displayMessage(message) shouldBe true

            getLastInstance(displayer).shouldNotBeNull()

            // Early dismiss before rendering_complete: messageView is still null.
            displayer.dismissCurrentInAppMessage()

            // backgroundDismissAndAwaitNextMessage is async on Default; wait briefly.
            var attempts = 0
            while (getLastInstance(displayer) != null && attempts < 50) {
                delay(20)
                attempts++
            }
        }

        getLastInstance(displayer).shouldBeNull()
        verify(atLeast = 1) { lifecycle.messageWasDismissed(message) }
    }
})

private fun getLastInstance(displayer: InAppDisplayer): WebViewManager? {
    val field = InAppDisplayer::class.java.getDeclaredField("lastInstance")
    field.isAccessible = true
    return field.get(displayer) as WebViewManager?
}
