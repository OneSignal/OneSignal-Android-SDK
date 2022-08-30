package com.onesignal.onesignal.core

import android.content.Context
import com.onesignal.onesignal.core.internal.OneSignalImp
import com.onesignal.onesignal.core.internal.service.IServiceProvider
import com.onesignal.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.INotificationsManager

/**
 * This singleton class is the entry point to the OneSignal SDK. It
 * is designed to make OneSignal easy to use.
 *    - No instance management is required from the app developer.
 *    - This is a wrapper around an instance of OneSignalImp, no logic lives in this class
 *
 * Note: This does *not* implement [IOneSignal] because you cannot specify @JvmStatic on an
 * override function. The cleanliness that @JvmStatic gives when calling from java outweighed
 * the use of an interface.
*/
object OneSignal {
    /**
     * Whether the SDK has been initialized.
     */
    @JvmStatic
    val isInitialized: Boolean
        get() = oneSignal.isInitialized

    /**
     * The current SDK version as a string.
     */
    @JvmStatic
    val sdkVersion: String
        get() = oneSignal.sdkVersion

    /**
     * The user manager for accessing user-scoped
     * management.
     */
    @JvmStatic
    val user: IUserManager
        get() = oneSignal.user

    /**
     * The notification manager for accessing device-scoped
     * notification management.
     */
    @JvmStatic
    val notifications: INotificationsManager
        get() = oneSignal.notifications

    /**
     * The location manager for accessing device-scoped
     * location management.
     */
    @JvmStatic
    val location: ILocationManager
        get() = oneSignal.location

    /**
     * The In App Messaging manager for accessing device-scoped
     * IAP management.
     */
    @JvmStatic
    val iam: IIAMManager
        get() = oneSignal.iam

    /**
     * Access to debug the SDK in the additional information is required to diagnose any
     * SDK-related issues.
     *
     * WARNING: This should not be used in a production setting.
     */
    @JvmStatic
    val debug: IDebugManager
        get() = oneSignal.debug

    /**
     * Determines whether a user must consent to privacy prior
     * to their user data being sent up to OneSignal.  This
     * should be set to `true` prior to the invocation of
     * [initWithContext] to ensure compliance.
     */
    @JvmStatic
    var requiresPrivacyConsent: Boolean
        get() = oneSignal.requiresPrivacyConsent
        set(value) { oneSignal.requiresPrivacyConsent = value }

    /**
     * Indicates whether privacy consent has been granted. This field is only relevant when
     * the application has opted into data privacy protections. See [requiresPrivacyConsent].
     */
    @JvmStatic
    var privacyConsent: Boolean
        get() = oneSignal.privacyConsent
        set(value) { oneSignal.privacyConsent = value }

    /**
     * Initialize the OneSignal SDK.  This should be called during startup of the application.
     *
     * @param context The Android context the SDK should use.
     */
    @JvmStatic
    fun initWithContext(context: Context) {
        oneSignal.initWithContext(context)
    }

    /**
     * Set the application ID the OneSignal SDK will be operating against.  This should be called
     * during startup of the application.  It can also be called again if the application ID needs
     * to be changed.  Changing the application ID will automatically log out any current user,
     * it's like starting over.
     *
     * @param appId The application ID the OneSignal SDK is bound to.
     */
    @JvmStatic
    fun setAppId(appId: String) {
        oneSignal.setAppId(appId)
    }

    /**
     * Log the SDK into OneSignal under the user identified by the [externalId] provided. The act of
     * logging a user into the OneSignal SDK will switch the [user] context to that specific user.
     *
     * * If the [externalId] exists the user will be retrieved and the context set from that
     *   user information. If operations have already been performed under a guest user the
     *   [IUserIdentityConflictResolver] specified in [setUserConflictResolver] will be called
     *   to determine how to proceed (defaulting to honor the remote user entirely).
     * * If the [externalId] does not exist the user will be created and the context set from
     *   the current local state. If operations have already been performed under a guest user
     *   those operations will be applied to the newly created user.
     *
     * *Push Notifications and In App Messaging*
     * Logging in a new user will automatically transfer push notification and in app messaging
     * subscriptions from the current user (if there is one) to the newly logged in user.  This is
     * because both Push and IAM are owned by the OS.
     */
    @JvmStatic
    suspend fun login(externalId: String): IUserManager = oneSignal.login(externalId)
    @JvmStatic
    suspend fun login(externalId: String, externalIdHash: String? = null): IUserManager = oneSignal.login(externalId, externalIdHash)

    /**
     * Log the SDK into OneSignal as a guest user. A guest user has no user identity that can later
     * be retrieved, except through this device as long as the app remains installed and the app
     * data is not cleared.
     */
    @JvmStatic
    suspend fun loginGuest(): IUserManager = oneSignal.loginGuest()

    @JvmStatic
    fun setUserConflictResolver(handler: IUserIdentityConflictResolver) = oneSignal.setUserConflictResolver(handler)

    @JvmStatic
    fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) = oneSignal.setUserConflictResolver(handler)

    private val oneSignal: IOneSignal by lazy {
        OneSignalImp()
    }

    internal val services: IServiceProvider
        get() = oneSignal as IServiceProvider

    /**
     * Inline function to retrieve a specific service
     */
    internal inline fun <reified T : Any> getService(): T {
        return services.getService(T::class.java)
    }

    internal inline fun <reified T : Any> getServiceOrNull(): T? {
        return services.getServiceOrNull(T::class.java)
    }
}
