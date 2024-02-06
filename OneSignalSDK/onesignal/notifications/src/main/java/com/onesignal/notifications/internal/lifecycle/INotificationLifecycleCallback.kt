package com.onesignal.notifications.internal.lifecycle

import android.app.Activity
import org.json.JSONObject

interface INotificationLifecycleCallback {
    /**
     * Called when a OneSignal notification bundle has been received by the SDK.  This
     * callback has the option to indicate to the bundle processor whether it should
     * continue processing the bundle, or allow the callback itself to handle the bundle.
     * The first callback to "claim" the bundle gets it.
     *
     * @param jsonPayload The bundle that has been received.
     *
     * @return True if the callback does *not* want the bundle processor to process the bundle, false otherwise.
     */
    suspend fun canReceiveNotification(jsonPayload: JSONObject): Boolean

    suspend fun canOpenNotification(
        activity: Activity,
        jsonData: JSONObject,
    ): Boolean
}
