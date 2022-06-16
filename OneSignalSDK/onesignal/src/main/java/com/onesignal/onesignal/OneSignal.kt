package com.onesignal.onesignal

import android.content.Context
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.internal.OneSignalImp
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.onesignal.user.Identity

/**
 * This singleton class is the entry point to the OneSignal SDK. It
 * is designed to make OneSignal easy to use.
 *    - No instance management is required from the app developer.
 *    - This is a wrapper around an instance of OneSignalImp, no logic lives in this class
 *
 * TODO: This does *not* implement IOneSignal because you cannot specify @JvmStatic on an override func. I thought the benefits of JvmStatic outweighed this, but looking for alternatives
*/
object OneSignal {

    private val oneSignal: IOneSignal by lazy {
        OneSignalImp()
    }

    @JvmStatic
    val sdkVersion: String
        get() = oneSignal.sdkVersion

    @JvmStatic
    val user: IUserManager
        get() = oneSignal.user

    @JvmStatic
    val notifications: INotificationsManager
        get() = oneSignal.notifications

    @JvmStatic
    val location: ILocationManager
        get() = oneSignal.location

    @JvmStatic
    val iam: IIAMManager
        get() = oneSignal.iam

    @JvmStatic
    val inForeground: Boolean
        get() = oneSignal.inForeground

    @JvmStatic
    val appEntryState: AppEntryAction
        get() = oneSignal.appEntryState

    @JvmStatic
    var requiresPrivacyConsent: Boolean
        get() = oneSignal.requiresPrivacyConsent
        set(value) { oneSignal.requiresPrivacyConsent = value }

    @JvmStatic
    suspend fun initWithContext(context: Context) {
        oneSignal.initWithContext(context)
    }

    @JvmStatic
    suspend fun setAppId(appId: String) {
        oneSignal.setAppId(appId)
    }

    @JvmStatic
    suspend fun login(identity: Identity): IUserManager {
        return oneSignal.login(identity);
    }

    @JvmStatic
    fun setUserConflictResolver(handler: IUserIdentityConflictResolver) = oneSignal.setUserConflictResolver(handler)

    @JvmStatic
    fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) = oneSignal.setUserConflictResolver(handler)
}
