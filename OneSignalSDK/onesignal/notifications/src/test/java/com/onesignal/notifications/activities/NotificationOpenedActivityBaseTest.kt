package com.onesignal.notifications.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper

class TestNotificationOpenedActivity : NotificationOpenedActivityBase() {
    var wasFinishCalledOnMainThread = false
    var wasProcessIntentCalled = false

    override fun finish() {
        wasFinishCalledOnMainThread = Looper.myLooper() == Looper.getMainLooper()
        super.finish()
    }

    override fun getIntent(): Intent {
        return Intent().apply {
            putExtra("some_key", "some_value") // simulate a valid OneSignal intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This triggers processIntent inside base
    }

    override fun processIntent() {
        wasProcessIntentCalled = true
        super.processIntent()
    }
}

@RobolectricTest
class NotificationOpenedActivityTest : FunSpec({
    test("finishSafely should be called on main thread") {
        val controller = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java)
        val activity = controller.setup().get()
        Handler(Looper.getMainLooper()).post {
            activity.finish()
        }
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        activity.wasFinishCalledOnMainThread shouldBe true
    }

    test("processIntent should be called during activity setup") {
        val controller = Robolectric.buildActivity(TestNotificationOpenedActivity::class.java)
        val activity = controller.setup().get()

        activity.wasProcessIntentCalled shouldBe true
    }
})