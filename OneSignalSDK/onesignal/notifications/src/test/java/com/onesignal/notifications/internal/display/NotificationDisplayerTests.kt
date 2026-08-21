package com.onesignal.notifications.internal.display

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.channels.impl.NotificationChannelManager
import com.onesignal.notifications.internal.common.NotificationGenerationJob
import com.onesignal.notifications.internal.display.impl.NotificationDisplayBuilder
import com.onesignal.notifications.internal.display.impl.NotificationDisplayer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.json.JSONObject
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val RESTORE_CHANNEL_ID = "restored_OS_notifications"
private const val APP_CHANNEL_ID = "app_high_importance"

@Config(
    packageName = "com.onesignal.example",
    sdk = [26],
)
@RobolectricTest
class NotificationDisplayerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    fun notificationManager(): NotificationManager =
        ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(NotificationManager::class.java)

    fun createAppChannel() {
        notificationManager().createNotificationChannel(
            NotificationChannel(APP_CHANNEL_ID, "App", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    suspend fun display(
        isRestoring: Boolean,
        extender: NotificationCompat.Extender?,
    ): NotificationGenerationJob {
        val applicationService = AndroidMockHelper.applicationService()
        val payload =
            JSONObject()
                .put("alert", "test message")
                .put("title", "test title")
                .put("custom", JSONObject().put("i", "UUID1"))

        val job = NotificationGenerationJob(payload, MockHelper.time(1111))
        job.isRestoring = isRestoring
        job.notification.setExtender(extender)

        val displayer =
            spyk(
                NotificationDisplayer(
                    applicationService,
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    NotificationDisplayBuilder(
                        applicationService,
                        NotificationChannelManager(applicationService, MockHelper.languageContext()),
                    ),
                ),
            )
        // displayNotification refuses to post from the main thread, which is where Robolectric runs.
        every { displayer.isRunningOnMainThreadCheck } returns Unit

        displayer.displayNotification(job)
        return job
    }

    fun postedChannelId(): String? = shadowOf(notificationManager()).allNotifications.last().channelId

    test("restored notification is posted to the silent restore channel") {
        // When
        display(isRestoring = true, extender = null)

        // Then
        postedChannelId() shouldBe RESTORE_CHANNEL_ID
    }

    test("restored notification stays on the restore channel when an extender picks another channel") {
        // Given
        createAppChannel()

        // When
        val job =
            display(isRestoring = true) {
                it.setChannelId(APP_CHANNEL_ID).setContentTitle("CUSTOM TITLE")
            }

        // Then
        // An app channel here would show every restored notification as a heads-up banner.
        postedChannelId() shouldBe RESTORE_CHANNEL_ID
        // Proves the extender actually ran, so the check above is not passing for free.
        job.overriddenTitleFromExtender shouldBe "CUSTOM TITLE"
    }

    test("notification that is not being restored keeps the channel its extender picked") {
        // Given
        createAppChannel()

        // When
        display(isRestoring = false) { it.setChannelId(APP_CHANNEL_ID) }

        // Then
        postedChannelId() shouldBe APP_CHANNEL_ID
    }

    test("sound an extender sets is recorded separately from the payload sound") {
        // Given
        val extenderSound = Uri.parse("content://media/internal/audio/media/7")

        // When
        val job = display(isRestoring = false) { it.setSound(extenderSound) }

        // Then
        // SummaryNotificationDisplayer compares these two on API 21-23, so they must stay separate.
        job.overriddenSound shouldBe extenderSound
        job.orgSound shouldNotBe extenderSound
    }
})
