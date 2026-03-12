package com.onesignal.notifications.internal.display

import androidx.core.app.NotificationCompat
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.display.impl.NotificationDisplayBuilder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [26],
)
@RobolectricTest
class NotificationDisplayBuilderTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    fun buildNotificationPriority(pri: Int?): Int {
        val channelManager = mockk<INotificationChannelManager>()
        every { channelManager.createNotificationChannel(any()) } returns "test_channel"

        val builder = NotificationDisplayBuilder(
            AndroidMockHelper.applicationService(),
            channelManager,
        )

        val payload = JSONObject()
            .put("alert", "test")
            .put("custom", JSONObject().put("i", "test-id"))
        if (pri != null) {
            payload.put("pri", pri)
        }

        val job = NotificationGenerationJob(payload, MockHelper.time(1111))
        val result = builder.getBaseOneSignalNotificationBuilder(job)
        return result.compatBuilder!!.build().priority
    }

    test("pri 10 should map to PRIORITY_MAX") {
        buildNotificationPriority(10) shouldBe NotificationCompat.PRIORITY_MAX
    }

    test("pri 9 should map to PRIORITY_MAX") {
        buildNotificationPriority(9) shouldBe NotificationCompat.PRIORITY_MAX
    }

    test("pri 8 should map to PRIORITY_HIGH") {
        buildNotificationPriority(8) shouldBe NotificationCompat.PRIORITY_HIGH
    }

    test("pri 7 should map to PRIORITY_HIGH") {
        buildNotificationPriority(7) shouldBe NotificationCompat.PRIORITY_HIGH
    }

    test("pri 6 should map to PRIORITY_DEFAULT") {
        buildNotificationPriority(6) shouldBe NotificationCompat.PRIORITY_DEFAULT
    }

    test("pri 5 should map to PRIORITY_DEFAULT") {
        buildNotificationPriority(5) shouldBe NotificationCompat.PRIORITY_DEFAULT
    }

    test("pri 4 should map to PRIORITY_LOW") {
        buildNotificationPriority(4) shouldBe NotificationCompat.PRIORITY_LOW
    }

    test("pri 3 should map to PRIORITY_LOW") {
        buildNotificationPriority(3) shouldBe NotificationCompat.PRIORITY_LOW
    }

    test("pri 2 should map to PRIORITY_MIN") {
        buildNotificationPriority(2) shouldBe NotificationCompat.PRIORITY_MIN
    }

    test("pri 1 should map to PRIORITY_MIN") {
        buildNotificationPriority(1) shouldBe NotificationCompat.PRIORITY_MIN
    }

    test("missing pri should default to PRIORITY_DEFAULT") {
        buildNotificationPriority(null) shouldBe NotificationCompat.PRIORITY_DEFAULT
    }

    // Regression: pri=9 previously mapped to PRIORITY_HIGH due to strict > 9 check.
    // The backend sends pri=9 for the highest dashboard priority, so this must yield
    // PRIORITY_MAX to match competitor notification ranking behavior.
    test("regression - pri 9 must not map to PRIORITY_HIGH") {
        buildNotificationPriority(9) shouldBe NotificationCompat.PRIORITY_MAX
        buildNotificationPriority(9) shouldNotBe NotificationCompat.PRIORITY_HIGH
    }
})
