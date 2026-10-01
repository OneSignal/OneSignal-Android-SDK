package com.onesignal.notifications.internal.restoration

import android.content.Context
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk

class NotificationRestoreWorkManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("enqueue failure clears the restored flag so a later focus can retry") {
        val manager = NotificationRestoreWorkManager()
        val context = mockk<Context>(relaxed = true)

        shouldThrow<Throwable> { manager.beginEnqueueingWork(context, false) }
        shouldThrow<Throwable> { manager.beginEnqueueingWork(context, false) }
    }
})
