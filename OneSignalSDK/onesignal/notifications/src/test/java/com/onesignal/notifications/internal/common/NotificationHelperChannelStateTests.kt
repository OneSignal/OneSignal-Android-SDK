package com.onesignal.notifications.internal.common

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.NotificationChannelState
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [33],
)
@RobolectricTest
class NotificationHelperChannelStateTests : FunSpec({
    val context = ApplicationProvider.getApplicationContext<Context>()
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
    }

    test("returns ENABLED when the channel is enabled") {
        val channel = NotificationChannel("enabled", "Enabled", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)

        NotificationHelper.getNotificationChannelState(context, channel.id) shouldBe
            NotificationChannelState.ENABLED
    }

    test("returns DISABLED when the channel importance is NONE") {
        val channel = NotificationChannel("disabled", "Disabled", NotificationManager.IMPORTANCE_NONE)
        notificationManager.createNotificationChannel(channel)

        NotificationHelper.getNotificationChannelState(context, channel.id) shouldBe
            NotificationChannelState.DISABLED
    }

    test("returns NOT_FOUND when the channel does not exist") {
        NotificationHelper.getNotificationChannelState(context, "missing") shouldBe
            NotificationChannelState.NOT_FOUND
    }

    test("returns NOT_FOUND when the channel was deleted") {
        val channel = NotificationChannel("deleted", "Deleted", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
        notificationManager.deleteNotificationChannel(channel.id)

        NotificationHelper.getNotificationChannelState(context, channel.id) shouldBe
            NotificationChannelState.NOT_FOUND
    }

    test("returns ENABLED independently of app-level notification permission") {
        val channel = NotificationChannel("app-disabled", "App disabled", NotificationManager.IMPORTANCE_DEFAULT)
        notificationManager.createNotificationChannel(channel)
        ShadowRoboNotificationManager.setShadowNotificationsEnabled(false)

        NotificationHelper.getNotificationChannelState(context, channel.id) shouldBe
            NotificationChannelState.ENABLED
    }

    test("returns DISABLED when the channel group is blocked") {
        val channel = NotificationChannel("group-disabled", "Group disabled", NotificationManager.IMPORTANCE_DEFAULT)
        channel.group = "blocked-group"
        val group = mockk<NotificationChannelGroup>()
        val manager = mockk<NotificationManager>()
        val mockContext = mockk<Context>()
        every { group.isBlocked } returns true
        every { manager.getNotificationChannel(channel.id) } returns channel
        every { manager.getNotificationChannelGroup(channel.group) } returns group
        every { mockContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        NotificationHelper.getNotificationChannelState(mockContext, channel.id) shouldBe
            NotificationChannelState.DISABLED
    }

    test("returns UNKNOWN when the channel state cannot be read") {
        val manager = mockk<NotificationManager>()
        val mockContext = mockk<Context>()
        every { mockContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager
        every { manager.getNotificationChannel("unknown") } throws
            IllegalStateException("Notification service unavailable")

        NotificationHelper.getNotificationChannelState(mockContext, "unknown") shouldBe
            NotificationChannelState.UNKNOWN
    }
})

@Config(
    packageName = "com.onesignal.example",
    sdk = [26],
)
@RobolectricTest
class NotificationHelperChannelStateOreoTests : FunSpec({
    test("does not check channel group state before Android Pie") {
        val channel = NotificationChannel("oreo-group", "Oreo group", NotificationManager.IMPORTANCE_DEFAULT)
        channel.group = "group"
        val manager = mockk<NotificationManager>()
        val context = mockk<Context>()
        every { manager.getNotificationChannel(channel.id) } returns channel
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns manager

        NotificationHelper.getNotificationChannelState(context, channel.id) shouldBe
            NotificationChannelState.ENABLED
    }
})

@Config(
    packageName = "com.onesignal.example",
    sdk = [25],
)
@RobolectricTest
class NotificationHelperChannelStatePreOreoTests : FunSpec({
    test("returns NOT_SUPPORTED before Android Oreo") {
        NotificationHelper.getNotificationChannelState(mockk(), "channel") shouldBe
            NotificationChannelState.NOT_SUPPORTED
    }
})
