package com.onesignal.notifications.internal.lifecycle

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.analytics.IAnalyticsTracker
import com.onesignal.notifications.internal.backend.INotificationBackendService
import com.onesignal.notifications.internal.lifecycle.impl.NotificationLifecycleService
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptWorkManager
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.subscriptions.IPushSubscription
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.Robolectric

private class Mocks {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val applicationService =
        run {
            val applicationService = ApplicationService()
            applicationService.start(context)
            applicationService
        }

    val mockSubscriptionManager: ISubscriptionManager =
        run {
            val mockSubManager = mockk<ISubscriptionManager>()
            every { mockSubManager.subscriptions.push } returns
                mockk<IPushSubscription>().apply { every { id } returns "UUID1" }
            mockSubManager
        }

    val notificationLifecycleService =
        spyk(
            NotificationLifecycleService(
                applicationService,
                MockHelper.time(0),
                MockHelper.configModelStore(),
                mockk<IInfluenceManager>().apply {
                    every { onDirectInfluenceFromNotification(any()) } returns Unit
                },
                mockSubscriptionManager,
                mockk<IDeviceService>().apply {
                    every { deviceType } returns IDeviceService.DeviceType.Android
                },
                mockk<INotificationBackendService>().apply {
                    coEvery { updateNotificationAsOpened(any(), any(), any(), any()) } coAnswers {
                        // assume every updateNotificationAsOpened call takes 5 ms
                        delay(5)
                        Unit
                    }
                },
                mockk<IReceiveReceiptWorkManager>(),
                mockk<IAnalyticsTracker>().apply {
                    every { trackOpenedEvent(any(), any()) } returns Unit
                },
            ),
        )

    val activity: Activity =
        run {
            val activityController = Robolectric.buildActivity(Activity::class.java)
            activityController.setup() // Moves Activity to RESUMED state
            activityController.get()
        }
}

@RobolectricTest
class NotificationLifecycleServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
    }

    test("Fires openDestinationActivity") {
        // Given
        val mocks = Mocks()
        val notificationLifecycleService = mocks.notificationLifecycleService
        val activity = mocks.activity

        // When
        val payload =
            JSONArray()
                .put(
                    JSONObject()
                        .put("alert", "test message")
                        .put(
                            "custom",
                            JSONObject()
                                .put("i", "UUID1"),
                        ),
                )
        notificationLifecycleService.notificationOpened(activity, payload)

        // Then
        coVerify(exactly = 1) {
            notificationLifecycleService.openDestinationActivity(
                withArg { Any() },
                withArg { Any() },
            )
        }
    }

    test("ensure notificationOpened makes backend updates in a background process") {
        // Given
        val mocks = Mocks()
        val notificationLifecycleService = mocks.notificationLifecycleService
        val activity = mocks.activity

        // When
        val payload = JSONArray()
        for (i in 1..1000) {
            // adding 1000 different notifications
            payload.put(
                JSONObject()
                    .put("alert", "test message")
                    .put(
                        "custom",
                        JSONObject()
                            .put("i", "UUID$i"),
                    ),
            )
        }

        withTimeout(500) {
            // 1000 notifications should be handled within a small amount of time
            notificationLifecycleService.notificationOpened(activity, payload)
        }

        // Then
        coVerify(exactly = 1) {
            // ensure openDestinationActivity is called within the timeout, prove that the increasing
            // number of notifications clicked does not delay the main thread proportionally
            notificationLifecycleService.openDestinationActivity(
                withArg { Any() },
                withArg { Any() },
            )
        }
    }
})
