package com.onesignal.notifications.internal.bundle

import android.content.Context
import android.os.Bundle
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.bundle.impl.NotificationBundleProcessor
import com.onesignal.notifications.internal.generation.INotificationGenerationWorkManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.json.JSONObject
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    sdk = [26],
)
@RobolectricTest
class NotificationBundleProcessorTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    fun buildOneSignalBundle(pri: String): Bundle {
        val bundle = Bundle()
        bundle.putString("custom", JSONObject().put("i", "test-notif-id").toString())
        bundle.putString("alert", "test message")
        bundle.putString("pri", pri)
        return bundle
    }

    fun captureIsHighPriority(pri: String): Boolean {
        val isHighPrioritySlot = slot<Boolean>()
        val workManager = mockk<INotificationGenerationWorkManager>()
        every {
            workManager.beginEnqueueingWork(
                any(), any(), any(), any(), any(), any(),
                capture(isHighPrioritySlot),
            )
        } returns true

        val processor = NotificationBundleProcessor(workManager, MockHelper.time(1111))
        val context = mockk<Context>(relaxed = true)
        processor.processBundleFromReceiver(context, buildOneSignalBundle(pri))

        return isHighPrioritySlot.captured
    }

    test("pri 10 should be treated as high priority") {
        captureIsHighPriority("10") shouldBe true
    }

    test("pri 9 should be treated as high priority") {
        captureIsHighPriority("9") shouldBe true
    }

    test("pri 8 should not be treated as high priority") {
        captureIsHighPriority("8") shouldBe false
    }

    // Regression: pri=9 was previously not treated as high priority due to strict > 9 check.
    // The backend sends pri=9 for the highest dashboard priority setting, so it must be
    // classified as high priority for correct work manager scheduling.
    test("regression - pri 9 must be classified as high priority") {
        captureIsHighPriority("9") shouldBe true
    }
})
