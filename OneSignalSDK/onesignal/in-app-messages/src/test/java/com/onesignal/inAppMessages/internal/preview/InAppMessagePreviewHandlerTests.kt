package com.onesignal.inAppMessages.internal.preview

import android.app.Activity
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.INotificationActivityOpener
import com.onesignal.notifications.internal.display.INotificationDisplayer
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [26],
)
@RobolectricTest
class InAppMessagePreviewHandlerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("canReceiveNotification displays IAM and returns false when notification has a IAM preview id and in foreground") {
        // Given
        val mockInAppDisplayer = mockk<IInAppDisplayer>()
        coEvery { mockInAppDisplayer.displayPreviewMessage(any()) } returns true

        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        val mockNotificationActivityOpener = mockk<INotificationActivityOpener>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true

        val stateService = InAppStateService()

        val inAppMessagePreviewHandler =
            InAppMessagePreviewHandler(
                mockInAppDisplayer,
                mockApplicationService,
                mockNotificationDisplayer,
                mockNotificationActivityOpener,
                mockNotificationLifecycleService,
                stateService,
                MockHelper.time(1111),
            )

        val jsonObject =
            JSONObject()
                .put(
                    "custom",
                    JSONObject()
                        .put(
                            "a",
                            JSONObject()
                                .put("os_in_app_message_preview_id", "previewUUID"),
                        ),
                )

        // When
        val response = inAppMessagePreviewHandler.canReceiveNotification(jsonObject)

        // Then
        response shouldBe false
        coVerify(exactly = 1) { mockInAppDisplayer.displayPreviewMessage("previewUUID") }
    }

    test("canReceiveNotification displays notification and returns false when notification has a IAM preview id and in background") {
        // Given
        val mockInAppDisplayer = mockk<IInAppDisplayer>()
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        coEvery { mockNotificationDisplayer.displayNotification(any()) } returns true
        val mockNotificationActivityOpener = mockk<INotificationActivityOpener>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns false

        val stateService = InAppStateService()

        val inAppMessagePreviewHandler =
            InAppMessagePreviewHandler(
                mockInAppDisplayer,
                mockApplicationService,
                mockNotificationDisplayer,
                mockNotificationActivityOpener,
                mockNotificationLifecycleService,
                stateService,
                MockHelper.time(1111),
            )

        val jsonObject =
            JSONObject()
                .put(
                    "custom",
                    JSONObject()
                        .put(
                            "a",
                            JSONObject()
                                .put("os_in_app_message_preview_id", "previewUUID"),
                        ),
                )

        // When
        val response = inAppMessagePreviewHandler.canReceiveNotification(jsonObject)

        // Then
        response shouldBe false
        coVerify(exactly = 1) { mockNotificationDisplayer.displayNotification(any()) }
    }

    test("canReceiveNotification returns true when notification does not have an IAM preview id") {
        // Given
        val mockInAppDisplayer = mockk<IInAppDisplayer>()
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        val mockNotificationActivityOpener = mockk<INotificationActivityOpener>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        val mockApplicationService = MockHelper.applicationService()

        val stateService = InAppStateService()

        val inAppMessagePreviewHandler =
            InAppMessagePreviewHandler(
                mockInAppDisplayer,
                mockApplicationService,
                mockNotificationDisplayer,
                mockNotificationActivityOpener,
                mockNotificationLifecycleService,
                stateService,
                MockHelper.time(1111),
            )

        val jsonObject =
            JSONObject()
                .put(
                    "custom",
                    JSONObject()
                        .put(
                            "a",
                            JSONObject(),
                        ),
                )

        // When
        val response = inAppMessagePreviewHandler.canReceiveNotification(jsonObject)

        // Then
        response shouldBe true
    }

    test("canOpenNotification displays IAM and returns false when notification has a IAM preview id") {
        // Given
        val mockInAppDisplayer = mockk<IInAppDisplayer>()
        coEvery { mockInAppDisplayer.displayPreviewMessage(any()) } returns true
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        val mockNotificationActivityOpener = mockk<INotificationActivityOpener>()
        coEvery { mockNotificationActivityOpener.openDestinationActivity(any(), any()) } just runs
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        val mockApplicationService = MockHelper.applicationService()

        val stateService = InAppStateService()

        val inAppMessagePreviewHandler =
            InAppMessagePreviewHandler(
                mockInAppDisplayer,
                mockApplicationService,
                mockNotificationDisplayer,
                mockNotificationActivityOpener,
                mockNotificationLifecycleService,
                stateService,
                MockHelper.time(1111),
            )

        val jsonObject =
            JSONObject()
                .put(
                    "custom",
                    JSONObject()
                        .put(
                            "a",
                            JSONObject()
                                .put("os_in_app_message_preview_id", "previewUUID"),
                        ),
                )

        val activity: Activity
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }

        // When
        val response = inAppMessagePreviewHandler.canOpenNotification(activity, jsonObject)

        // Then
        response shouldBe false
        coVerify(exactly = 1) {
            mockNotificationActivityOpener.openDestinationActivity(
                activity,
                withArg {
                    it.length() shouldBe 1
                    it.getJSONObject(0) shouldBe jsonObject
                },
            )
        }
        coVerify(exactly = 1) { mockInAppDisplayer.displayPreviewMessage("previewUUID") }
    }

    test("canOpenNotification returns true when notification has no IAM preview id") {
        // Given
        val mockInAppDisplayer = mockk<IInAppDisplayer>()
        val mockNotificationDisplayer = mockk<INotificationDisplayer>()
        val mockNotificationActivityOpener = mockk<INotificationActivityOpener>()
        val mockNotificationLifecycleService = mockk<INotificationLifecycleService>()
        val mockApplicationService = MockHelper.applicationService()

        val stateService = InAppStateService()

        val inAppMessagePreviewHandler =
            InAppMessagePreviewHandler(
                mockInAppDisplayer,
                mockApplicationService,
                mockNotificationDisplayer,
                mockNotificationActivityOpener,
                mockNotificationLifecycleService,
                stateService,
                MockHelper.time(1111),
            )

        val jsonObject =
            JSONObject()
                .put(
                    "custom",
                    JSONObject()
                        .put(
                            "a",
                            JSONObject(),
                        ),
                )

        val activity: Activity
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }

        // When
        val response = inAppMessagePreviewHandler.canOpenNotification(activity, jsonObject)

        // Then
        response shouldBe true
    }
})
