package com.onesignal.notification.internal.common

import android.content.Context
import com.onesignal.core.internal.common.AndroidUtils

/***
 * Settings that effect the OneSignal notification open behavior at the app level.
 */
internal object OSNotificationOpenAppSettings {

    /***
     * When the notification is tapped on should it show an Activity?
     * This could be resuming / opening the app or opening the URL on the notification.
     */
    fun getShouldOpenActivity(context: Context): Boolean {
        return "DISABLE" != AndroidUtils.getManifestMeta(
            context,
            "com.onesignal.NotificationOpened.DEFAULT"
        )
    }

    /***
     * Should the default behavior of OneSignal be to always open URLs be disabled?
     */
    fun getSuppressLaunchURL(context: Context): Boolean {
        return AndroidUtils.getManifestMetaBoolean(context, "com.onesignal.suppressLaunchURLs")
    }
}
