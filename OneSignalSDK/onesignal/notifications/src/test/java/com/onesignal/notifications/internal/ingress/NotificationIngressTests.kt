package com.onesignal.notifications.internal.ingress

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

@RobolectricTest
class NotificationIngressTests : FunSpec({
    lateinit var context: Context

    beforeAny {
        context = ApplicationProvider.getApplicationContext()
        NotificationIngress.resetForTest(context)
    }

    test("FCM input remains durable when drain scheduling fails") {
        NotificationIngress.drainSchedulerForTest = { throw IllegalStateException("scheduler unavailable") }
        val bundle =
            Bundle().apply {
                putString("custom", """{"i":"notification-id"}""")
                putString("alert", "message")
            }

        NotificationIngress.persistFcm(
            context,
            Intent("com.google.android.c2dm.intent.RECEIVE"),
            bundle,
        ) shouldBe true

        NotificationIngress.pendingCountForTest(context) shouldBe 1
    }

    test("duplicate FCM input replaces the same durable record") {
        NotificationIngress.drainSchedulerForTest = {}
        val bundle = Bundle().apply { putString("custom", """{"i":"notification-id"}""") }
        val intent = Intent("com.google.android.c2dm.intent.RECEIVE")

        NotificationIngress.persistFcm(context, intent, bundle)
        NotificationIngress.persistFcm(context, intent, bundle)

        NotificationIngress.pendingCountForTest(context) shouldBe 1
    }

    test("dismiss input is persisted before scheduling") {
        var countAtSchedule = 0
        NotificationIngress.drainSchedulerForTest = {
            countAtSchedule = NotificationIngress.pendingCountForTest(context)
        }
        val intent =
            Intent().apply {
                putExtra("androidNotificationId", 42)
                putExtra("dismissed", true)
            }

        NotificationIngress.persistDismiss(context, intent)

        countAtSchedule shouldBe 1
    }
})
