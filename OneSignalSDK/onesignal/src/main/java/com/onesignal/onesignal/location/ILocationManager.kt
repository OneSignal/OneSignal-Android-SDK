package com.onesignal.onesignal.location

interface ILocationManager {
    /**
     * Whether location is currently shared
     */
    var isLocationShared: Boolean

    /**
     * Use this method to manually prompt the user for location permissions.
     * This allows for geotagging so you send notifications to users based on location.
     *
     * Make sure you have one of the following permission in your `AndroidManifest.xml` as well.
     *
     *     <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
     *     <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
     *
     * Be aware of best practices regarding asking permissions on Android:
     * [Requesting Permissions | Android Developers] (https://developer.android.com/guide/topics/permissions/requesting.html)
     *
     * @see [Permission Requests | OneSignal Docs](https://documentation.onesignal.com/docs/permission-requests)
     */
    suspend fun requestPermission() : Boolean?
}
