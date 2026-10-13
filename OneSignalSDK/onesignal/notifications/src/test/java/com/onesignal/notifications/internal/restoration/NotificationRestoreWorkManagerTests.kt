package com.onesignal.notifications.internal.restoration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [28])
class NotificationRestoreWorkManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        mockkObject(OSWorkManagerHelper)
        NotificationRestoreWorkManager.resetForTest()
    }

    afterAny {
        NotificationRestoreWorkManager.resetForTest()
        unmockkObject(OSWorkManagerHelper)
    }

    test("the restore work is enqueued once per process") {
        val workManager = mockk<WorkManager>(relaxed = true)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        val manager = NotificationRestoreWorkManager()
        val context = ApplicationProvider.getApplicationContext<Context>()

        manager.beginEnqueueingWork(context, true)
        manager.beginEnqueueingWork(context, true)

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any<String>(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        }
    }

    test("enqueue failure clears the restored flag so a later focus can retry") {
        every { OSWorkManagerHelper.getInstance(any()) } throws IllegalStateException("WorkManager is not initialized")
        val manager = NotificationRestoreWorkManager()
        val context = ApplicationProvider.getApplicationContext<Context>()

        shouldThrow<Throwable> { manager.beginEnqueueingWork(context, false) }
        shouldThrow<Throwable> { manager.beginEnqueueingWork(context, false) }
    }
})
