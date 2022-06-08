package com.onesignal.onesignal.internal.push

import com.onesignal.onesignal.internal.common.OSObservable
import com.onesignal.onesignal.internal.common.OSUtils
import com.onesignal.onesignal.notification.IOSPermissionState
import org.json.JSONObject

class OSPermissionState internal constructor(asFrom: Boolean) : Cloneable, Cloneable,
    IOSPermissionState {
    override val observable: OSObservable<Any, OSPermissionState>
    override var notificationsEnabled = false
    fun refreshAsTo() {
        setNotificationsEnabled(OSUtils.areNotificationsEnabled(OneSignal.appContext))
    }

    fun areNotificationsEnabled(): Boolean {
        return notificationsEnabled
    }

    private fun setNotificationsEnabled(set: Boolean) {
        val changed = notificationsEnabled != set
        notificationsEnabled = set
        if (changed) observable.notifyChange(this)
    }

    fun persistAsFrom() {
        OneSignalPrefs.saveBool(
            OneSignalPrefs.PREFS_ONESIGNAL,
            OneSignalPrefs.PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST, notificationsEnabled
        )
    }

    fun compare(from: OSPermissionState): Boolean {
        return notificationsEnabled != from.notificationsEnabled
    }

    override fun clone(): Any {
        try {
            return super.clone()
        } catch (t: Throwable) {
        }
        return null
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put(ARE_NOTIFICATION_ENABLED_KEY, notificationsEnabled)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return mainObj
    }

    override fun toString(): String {
        return toJSONObject().toString()
    } // FUTURE: Can add a list of categories here for Android O.

    companion object {
        private const val ARE_NOTIFICATION_ENABLED_KEY = "areNotificationsEnabled"
        private const val CHANGED_KEY = "changed"
    }

    init {
        // Java 8 method reference can be used in the future with Android Studio 2.4.0
        //   OSPermissionChangedInternalObserver::changed
        observable = OSObservable(
            CHANGED_KEY,
            false
        )
        if (asFrom) {
            notificationsEnabled = OneSignalPrefs.getBool(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_ONESIGNAL_ACCEPTED_NOTIFICATION_LAST, false
            )
        } else {
            refreshAsTo()
        }
    }
}