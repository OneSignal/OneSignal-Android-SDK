package com.onesignal.notifications.internal.common

import android.os.Bundle
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.json.JSONObject

@RobolectricTest
class NotificationFormatHelperTests : FunSpec({
    test("getOSNotificationIdFromJson returns the id from custom") {
        val json = JSONObject().put("custom", """{"i":"notification-id"}""")

        NotificationFormatHelper.getOSNotificationIdFromJson(json) shouldBe "notification-id"
    }

    test("getOSNotificationIdFromJson returns null when custom is missing") {
        val json = JSONObject().put("type", "call")

        NotificationFormatHelper.getOSNotificationIdFromJson(json) shouldBe null
    }

    test("getOSNotificationIdFromJson returns null when custom is not JSON") {
        val json = JSONObject().put("custom", "not-json")

        NotificationFormatHelper.getOSNotificationIdFromJson(json) shouldBe null
    }

    test("isOneSignalBundle is false when custom is missing") {
        NotificationFormatHelper.isOneSignalBundle(Bundle().apply { putString("type", "call") }) shouldBe false
    }
})
