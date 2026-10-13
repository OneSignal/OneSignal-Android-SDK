package com.onesignal.notifications.internal.generation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationWorkManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.json.JSONObject
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [28])
class NotificationGenerationEnqueueTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        mockkObject(OSWorkManagerHelper)
    }

    afterAny {
        unmockkObject(OSWorkManagerHelper)
    }

    test("a new notification is enqueued as unique work under its OneSignal id") {
        val workManager = mockk<WorkManager>(relaxed = true)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        val context = ApplicationProvider.getApplicationContext<Context>()
        val payload = JSONObject().put("custom", JSONObject().put("i", "notif-enqueue").toString())

        val accepted =
            NotificationGenerationWorkManager().beginEnqueueingWork(
                context,
                "notif-enqueue",
                1,
                payload,
                1L,
                null,
                false,
            )

        accepted shouldBe true
        verify(exactly = 1) {
            workManager.enqueueUniqueWork("notif-enqueue", ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        }
    }

    test("a payload with no OneSignal id is rejected before any work is enqueued") {
        val workManager = mockk<WorkManager>(relaxed = true)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        val context = ApplicationProvider.getApplicationContext<Context>()

        val accepted =
            NotificationGenerationWorkManager().beginEnqueueingWork(
                context,
                "notif-no-id",
                1,
                JSONObject().put("custom", JSONObject().toString()),
                1L,
                null,
                false,
            )

        accepted shouldBe false
        verify(exactly = 0) { workManager.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) }
    }
})
