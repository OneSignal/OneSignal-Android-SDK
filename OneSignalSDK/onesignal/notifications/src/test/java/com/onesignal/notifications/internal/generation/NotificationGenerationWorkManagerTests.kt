package com.onesignal.notifications.internal.generation

import android.content.Context
import androidx.work.Data
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.common.NotificationRestoreReason
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationWorkManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.json.JSONObject

class NotificationGenerationWorkManagerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("readRestoreReason returns the enum written by enqueue") {
        val data =
            Data.Builder()
                .putString("restore_reason", NotificationRestoreReason.GROUP_REGROUP.name)
                .build()

        NotificationGenerationWorkManager.readRestoreReason(data) shouldBe NotificationRestoreReason.GROUP_REGROUP
    }

    test("readRestoreReason treats legacy is_restoring true as a shade restore") {
        val data =
            Data.Builder()
                .putBoolean("is_restoring", true)
                .build()

        NotificationGenerationWorkManager.readRestoreReason(data) shouldBe NotificationRestoreReason.SHADE_RESTORE
    }

    test("readRestoreReason treats legacy is_restoring false as a new notification") {
        val data =
            Data.Builder()
                .putBoolean("is_restoring", false)
                .build()

        NotificationGenerationWorkManager.readRestoreReason(data) shouldBe null
    }

    test("readRestoreReason fails closed on an unknown enum name") {
        // Only restores write a reason, so a name from a newer version (work drained after a
        // downgrade) must stay quiet rather than present as a fresh push.
        val data =
            Data.Builder()
                .putString("restore_reason", "NOT_A_REASON")
                .build()

        NotificationGenerationWorkManager.readRestoreReason(data) shouldBe NotificationRestoreReason.SHADE_RESTORE
    }

    test("enqueue failure forgets the notification id so a later delivery can retry") {
        val manager = NotificationGenerationWorkManager()
        val context = mockk<Context>(relaxed = true)
        val json = JSONObject().put("custom", JSONObject().put("i", "notif-1").toString())

        shouldThrow<Throwable> {
            manager.beginEnqueueingWork(context, "notif-1", 1, json, 1L, null, false)
        }
        shouldThrow<Throwable> {
            manager.beginEnqueueingWork(context, "notif-1", 1, json, 1L, null, false)
        }
    }
})
