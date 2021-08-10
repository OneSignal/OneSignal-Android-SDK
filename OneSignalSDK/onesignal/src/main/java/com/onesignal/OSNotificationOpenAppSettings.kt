package com.onesignal

import android.content.Context

/***
 * Settings that effect the OneSignal notification open behavior at the app level.
 */
object OSNotificationOpenAppSettings {

    /***
     * When the notification is tapped on should it show an Activity?
     * This could be resuming / opening the app or opening the URL on the notification.
     */
    fun getShouldOpenActivity(context: Context): Boolean {
        return "DISABLE" != OSUtils.getManifestMeta(
            context,
            "com.onesignal.NotificationOpened.DEFAULT"
        )
    }

    /***
     * Should the default behavior of OneSignal be to always open URLs be disabled?
     */
    fun getSuppressLaunchURL(context: Context): Boolean {
        return OSUtils.getManifestMetaBoolean(
            context,
            "com.onesignal.suppressLaunchURLs"
        )
    }
}