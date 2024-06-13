/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.notifications.shadows

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.robolectric.Shadows
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotification
import org.robolectric.shadows.ShadowNotificationManager
import java.util.ArrayList
import java.util.LinkedHashMap

@Implements(value = NotificationManager::class, looseSignatures = true)
class ShadowRoboNotificationManager : ShadowNotificationManager() {
    inner class PostedNotification internal constructor(var id: Int, var notif: Notification) {
        val shadow: ShadowNotification
            get() = Shadows.shadowOf(notif)
    }

    public override fun cancelAll() {
        val showingNotifs = notifications.keys
        notifications.clear()
        cancelledNotifications.addAll(showingNotifs)
    }

    public override fun cancel(id: Int) {
        notifications.remove(id)
        cancelledNotifications.add(id)
    }

    public override fun cancel(
        tag: String,
        id: Int,
    ) {
        notifications.remove(id)
        cancelledNotifications.add(id)
    }

    public override fun notify(
        tag: String,
        id: Int,
        notification: Notification,
    ) {
        lastNotif = notification
        lastNotifId = id
        notifications[id] =
            PostedNotification(
                id,
                notification,
            )
        super.notify(tag, id, notification)
    }

    fun createNotificationChannel(channel: NotificationChannel?) {
        lastChannel = channel
        super.createNotificationChannel(channel as Any?)
    }

    fun createNotificationChannelGroup(group: NotificationChannelGroup?) {
        lastChannelGroup = group
        super.createNotificationChannelGroup(group as Any?)
    }

    companion object {
        var lastNotif: Notification? = null
            private set
        val lastShadowNotif: ShadowNotification
            get() = Shadows.shadowOf(lastNotif)
        var lastNotifId = 0
        val notifications = LinkedHashMap<Int, PostedNotification>()
        val cancelledNotifications = mutableListOf<Int>()

        fun reset() {
            notifications.clear()
            cancelledNotifications.clear()
            lastNotif = null
            lastNotifId = 0
        }

        private lateinit var mInstance: ShadowRoboNotificationManager

        fun getNotificationsInGroup(group: String): List<Notification> {
            val notifications: MutableList<Notification> = ArrayList()
            for (notification in mInstance.allNotifications) {
                if (NotificationCompat.isGroupSummary(notification)) continue
                if (group != notification.group) continue
                notifications.add(notification)
            }
            return notifications
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            mInstance.setNotificationsEnabled(enabled)
        }

        var lastChannel: NotificationChannel? = null
        var lastChannelGroup: NotificationChannelGroup? = null
    }

    init {
        mInstance = this
    }
}
