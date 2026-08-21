package com.onesignal.notifications.internal.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationWorkManager
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.json.JSONObject

@RobolectricTest
class WorkManagerEnqueueTests : FunSpec({
    lateinit var context: Context
    lateinit var workManager: WorkManager

    beforeAny {
        context = ApplicationProvider.getApplicationContext()
        workManager = mockk(relaxed = true)
        mockkObject(OSWorkManagerHelper)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        NotificationRestoreWorkManager.resetForTest()
    }

    afterAny {
        NotificationRestoreWorkManager.resetForTest()
        unmockkObject(OSWorkManagerHelper)
    }

    test("notification generation can be enqueued again after WorkManager rejects it") {
        val manager = NotificationGenerationWorkManager()
        val payload = JSONObject().put("custom", """{"i":"notification-id"}""")
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } throws IllegalStateException("enqueue failed")

        shouldThrow<IllegalStateException> {
            manager.beginEnqueueingWork(context, "notification-id", 42, payload, 1L, false, false)
        }

        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)
        manager.beginEnqueueingWork(context, "notification-id", 42, payload, 1L, false, false) shouldBe true
        verify(exactly = 2) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
        NotificationGenerationWorkManager.removeNotificationIdProcessed("notification-id")
    }

    test("notification restore can be enqueued again after WorkManager rejects it") {
        val manager = NotificationRestoreWorkManager()
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } throws IllegalStateException("enqueue failed")

        shouldThrow<IllegalStateException> {
            manager.beginEnqueueingWork(context, shouldDelay = true)
        }

        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)
        manager.beginEnqueueingWork(context, shouldDelay = false)
        verify(exactly = 2) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }
})
