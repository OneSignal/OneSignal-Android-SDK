package com.onesignal

import android.content.Context

/***
 * Settings that effect the OneSignal notification open behavior at the app level.
 */
object OSNotificationOpenAppSettings {

    /***
     * Should the default behavior of OneSignal be to always open / resume the app be disabled?
     */
    fun getDefaultAppOpenDisabled(context: Context): Boolean {
        return "DISABLE" == OSUtils.getManifestMeta(
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