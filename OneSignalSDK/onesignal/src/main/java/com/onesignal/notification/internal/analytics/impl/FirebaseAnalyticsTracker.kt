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
package com.onesignal.notification.internal.analytics.impl

import android.content.Context
import android.os.Bundle
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.time.ITime
import com.onesignal.notification.internal.analytics.IAnalyticsTracker
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicLong

internal class FirebaseAnalyticsTracker(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime
) : IAnalyticsTracker {

    private val isEnabled: Boolean
        get() {
            return _configModelStore.model.firebaseAnalytics
        }

    private var lastReceivedTime: AtomicLong? = null
    private var lastOpenedTime: AtomicLong? = null
    private var lastReceivedNotificationId: String? = null
    private var lastReceivedNotificationCampaign: String? = null
    private var mFirebaseAnalyticsInstance: Any? = null

    override fun trackInfluenceOpenEvent() {
        if (!isEnabled || lastReceivedTime == null || lastReceivedNotificationId == null) {
            return
        }

        // Attribute if app was opened in 2 minutes or less after displaying the notification
        val now = _time.currentTimeMillis
        if (now - lastReceivedTime!!.get() > 1000 * 60 * 2) return

        // Don't attribute if we opened a notification in the last 30 seconds.
        //  To prevent an open and an influenced open from firing for the same notification.
        if (lastOpenedTime != null && now - lastOpenedTime!!.get() < 1000 * 30) return
        try {
            val firebaseAnalyticsInstance = getFirebaseAnalyticsInstance()
            val trackMethod = getTrackMethod(FirebaseAnalyticsClass)
            val event = EVENT_NOTIFICATION_INFLUENCE_OPEN

            // construct the firebase analytics event bundle
            val bundle = Bundle()
            bundle.putString("source", "OneSignal")
            bundle.putString("medium", "notification")
            bundle.putString("notification_id", lastReceivedNotificationId!!)
            bundle.putString("campaign", lastReceivedNotificationCampaign!!)
            trackMethod!!.invoke(firebaseAnalyticsInstance, event, bundle)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun trackOpenedEvent(notificationId: String, campaign: String) {
        if (!isEnabled) {
            return
        }

        if (lastOpenedTime == null) {
            lastOpenedTime = AtomicLong()
        }

        lastOpenedTime!!.set(_time.currentTimeMillis)
        try {
            // get the source, medium, campaign params from the openResult
            val firebaseAnalyticsInstance = getFirebaseAnalyticsInstance()
            val trackMethod = getTrackMethod(FirebaseAnalyticsClass)

            // construct the firebase analytics event bundle
            val bundle = Bundle()
            bundle.putString("source", "OneSignal")
            bundle.putString("medium", "notification")
            bundle.putString("notification_id", notificationId)
            bundle.putString("campaign", campaign)
            trackMethod!!.invoke(firebaseAnalyticsInstance, EVENT_NOTIFICATION_OPENED, bundle)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun trackReceivedEvent(notificationId: String, campaign: String) {
        if (!isEnabled) {
            return
        }

        try {
            // get the source, medium, campaign params from the openResult
            val firebaseAnalyticsInstance = getFirebaseAnalyticsInstance()
            val trackMethod = getTrackMethod(FirebaseAnalyticsClass)
            // construct the firebase analytics event bundle
            val bundle = Bundle()
            bundle.putString("source", "OneSignal")
            bundle.putString("medium", "notification")
            bundle.putString("notification_id", notificationId)
            bundle.putString("campaign", campaign)
            trackMethod!!.invoke(firebaseAnalyticsInstance, EVENT_NOTIFICATION_RECEIVED, bundle)
            if (lastReceivedTime == null) lastReceivedTime = AtomicLong()
            lastReceivedTime!!.set(_time.currentTimeMillis)
            lastReceivedNotificationId = notificationId
            lastReceivedNotificationCampaign = campaign
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun getFirebaseAnalyticsInstance(): Any? {
        if (mFirebaseAnalyticsInstance == null) {
            val getInstanceMethod = getInstanceMethod(FirebaseAnalyticsClass)
            mFirebaseAnalyticsInstance = try {
                getInstanceMethod!!.invoke(null, _applicationService.appContext)
            } catch (e: Throwable) {
                e.printStackTrace()
                return null
            }
        }
        return mFirebaseAnalyticsInstance
    }

    companion object {
        private var FirebaseAnalyticsClass: Class<*>? = null
        private const val EVENT_NOTIFICATION_OPENED = "os_notification_opened"
        private const val EVENT_NOTIFICATION_INFLUENCE_OPEN = "os_notification_influence_open"
        private const val EVENT_NOTIFICATION_RECEIVED = "os_notification_received"

        fun canTrack(): Boolean {
            return try {
                FirebaseAnalyticsClass =
                    Class.forName("com.google.firebase.analytics.FirebaseAnalytics")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }

        private fun getTrackMethod(clazz: Class<*>?): Method? {
            return try {
                clazz!!.getMethod("logEvent", String::class.java, Bundle::class.java)
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
                null
            }
        }

        private fun getInstanceMethod(clazz: Class<*>?): Method? {
            return try {
                clazz!!.getMethod("getInstance", Context::class.java)
            } catch (e: NoSuchMethodException) {
                e.printStackTrace()
                null
            }
        }
    }
}
