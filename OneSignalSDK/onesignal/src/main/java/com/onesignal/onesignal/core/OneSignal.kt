package com.onesignal.onesignal.core

import android.content.Context
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.core.internal.service.IServiceProvider
import com.onesignal.onesignal.core.internal.OneSignalImp
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.core.user.Identity

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

    internal val services: IServiceProvider
        get() = oneSignal as IServiceProvider

    /**
     * Inline function to retrieve a specific service
     */
    internal inline fun <reified T: Any> getService(): T {
        return services.getService(T::class.java)
    }

    internal inline fun <reified T: Any> getServiceOrNull() : T? {
        return services.getServiceOrNull(T::class.java)
    }

    @JvmStatic
    val isInitialized: Boolean
        get() = oneSignal.isInitialized

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
    var requiresPrivacyConsent: Boolean
        get() = oneSignal.requiresPrivacyConsent
        set(value) { oneSignal.requiresPrivacyConsent = value }

    @JvmStatic
    fun initWithContext(context: Context) {
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
