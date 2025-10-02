package com.onesignal

import android.content.Context
import com.onesignal.common.services.IServiceProvider
import com.onesignal.debug.IDebugManager
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.internal.OneSignalImp
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager

/**
 * This singleton class is the entry point to the OneSignal SDK. It
 * is designed to make OneSignal easy to use.
 *
 * * No instance management is required from the app developer.
 * * This is a wrapper around an instance of [IOneSignal], no logic lives in this class.
 *
 * Note: This does *not* implement [IOneSignal] itself because you cannot specify @JvmStatic on an
 * override function. The cleanliness that @JvmStatic gives when calling from java outweighs
 * the use of an interface.  This class however should implement [IOneSignal] "in spirit".
*/
object OneSignal {
    /**
     * Whether the SDK has been initialized (i.e. [initWithContext] has been called).
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
     * The user manager for accessing user-scoped management.  Initialized only after [initWithContext]
     * has been called, and initialized with a device-scoped user until (or if) [login] has been
     * called.
     */
    @JvmStatic
    val User: IUserManager
        get() = oneSignal.user

    /**
     * The session manager for accessing session-scoped management.  Initialized only after [initWithContext]
     * has been called.
     */
    @JvmStatic
    val Session: ISessionManager
        get() = oneSignal.session

    /**
     * The notification manager for accessing device-scoped notification management. Initialized
     * only after [initWithContext] has been called.
     */
    @JvmStatic
    val Notifications: INotificationsManager
        get() = oneSignal.notifications

    /**
     * The location manager for accessing device-scoped location management. Initialized
     * only after [initWithContext] has been called.
     */
    @JvmStatic
    val Location: ILocationManager
        get() = oneSignal.location

    /**
     * The In App Messaging manager for accessing device-scoped IAP management. Initialized
     * only after [initWithContext] has been called.
     */
    @JvmStatic
    val InAppMessages: IInAppMessagesManager
        get() = oneSignal.inAppMessages

    /**
     * Access to debug the SDK in the additional information is required to diagnose any
     * SDK-related issues.  Initialized immediately (can be used prior to [initWithContext]).
     *
     * WARNING: This should not be used in a production setting.
     */
    @JvmStatic
    val Debug: IDebugManager
        get() = oneSignal.debug

    /**
     * Determines whether a user must consent to privacy prior
     * to their user data being sent up to OneSignal.  This
     * should be set to `true` prior to the invocation of
     * [initWithContext] to ensure compliance.
     */
    @JvmStatic
    var consentRequired: Boolean
        get() = oneSignal.consentRequired
        set(value) {
            oneSignal.consentRequired = value
        }

    /**
     * Indicates whether privacy consent has been granted. This field is only relevant when
     * the application has opted into data privacy protections. See [requiresPrivacyConsent].
     */
    @JvmStatic
    var consentGiven: Boolean
        get() = oneSignal.consentGiven
        set(value) {
            oneSignal.consentGiven = value
        }

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    @JvmStatic
    var disableGMSMissingPrompt: Boolean
        get() = oneSignal.disableGMSMissingPrompt
        set(value) {
            oneSignal.disableGMSMissingPrompt = value
        }

    /**
     * Initialize the OneSignal SDK.  This should be called during startup of the application.
     *
     * @param context The Android context the SDK should use.
     * @param appId The application ID the OneSignal SDK is bound to.
     */
    @JvmStatic
    fun initWithContext(
        context: Context,
        appId: String,
    ) {
        oneSignal.initWithContext(context, appId)
    }

    /**
     * Initialize the OneSignal SDK asynchronously. This should be called during startup of the application.
     * This method provides a suspended version that returns a boolean indicating success.
     * Uses Dispatchers.IO internally to prevent ANRs and optimize for I/O operations.
     *
     * @param context Application context is recommended for SDK operations
     * @param appId The application ID the OneSignal SDK is bound to.
     * @return Boolean indicating if initialization was successful.
     */
    @JvmStatic
    suspend fun initWithContextSuspend(
        context: Context,
        appId: String? = null,
    ): Boolean {
        return oneSignal.initWithContextSuspend(context, appId)
    }

    /**
     * Login to OneSignal under the user identified by the [externalId] provided. The act of
     * logging a user into the OneSignal SDK will switch the [User] context to that specific user.
     *
     * * If the [externalId] exists the user will be retrieved and the context set from that
     *   user information. If operations have already been performed under a guest user, they
     *   *will not* be applied to the now logged in user (they will be lost).
     * * If the [externalId] does not exist the user will be created and the context set from
     *   the current local state. If operations have already been performed under a guest user
     *   those operations *will* be applied to the newly created user.
     *
     * *Push Notifications and In App Messaging*
     * Logging in a new user will automatically transfer push notification and in app messaging
     * subscriptions from the current user (if there is one) to the newly logged in user.  This is
     * because both Push and IAM are owned by the device.
     *
     * @param externalId The external ID of the user that is to be logged in.
     */
    @JvmStatic
    fun login(externalId: String) = oneSignal.login(externalId)

    /**
     * Login to OneSignal under the user identified by the [externalId] provided. The act of
     * logging a user into the OneSignal SDK will switch the [User] context to that specific user.
     *
     * * If the [externalId] exists the user will be retrieved and the context set from that
     *   user information. If operations have already been performed under a guest user, they
     *   *will not* be applied to the now logged in user (they will be lost).
     * * If the [externalId] does not exist the user will be created and the context set from
     *   the current local state. If operations have already been performed under a guest user
     *   those operations *will* be applied to the newly created user.
     *
     * *Push Notifications and In App Messaging*
     * Logging in a new user will automatically transfer push notification and in app messaging
     * subscriptions from the current user (if there is one) to the newly logged in user.  This is
     * because both Push and IAM are owned by the device.
     *
     * @param externalId The external ID of the user that is to be logged in.
     * @param jwtBearerToken The optional JWT bearer token generated by your backend to establish
     * trust for the login operation.  Required when identity verification has been enabled. See
     * [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    @JvmStatic
    fun login(
        externalId: String,
        jwtBearerToken: String? = null,
    ) = oneSignal.login(externalId, jwtBearerToken)

    /**
     * Logout the user previously logged in via [login]. The [User] property now references
     * a new device-scoped user. A device-scoped user has no user identity that can later
     * be retrieved, except through this device as long as the app remains installed and the app
     * data is not cleared.
     */
    @JvmStatic
    fun logout() = oneSignal.logout()

    private val oneSignal: IOneSignal by lazy {
        OneSignalImp()
    }

    /**
     * Used to initialize the SDK when driven through user action. It is assumed [initWithContext]
     * has been called by the app developer, providing the appId, which has been cached for
     * this purpose.
     *
     * THIS IS AN INTERNAL INTERFACE AND SHOULD NOT BE USED DIRECTLY.
     */
    @JvmStatic
    suspend fun initWithContext(context: Context): Boolean {
        return oneSignal.initWithContext(context)
    }

    /**
     * Login a user with external ID and optional JWT token (suspend version).
     * Handles initialization automatically.
     *
     * @param context Application context is recommended for SDK operations
     * @param appId The OneSignal app ID
     * @param externalId External user ID for login
     * @param jwtBearerToken Optional JWT token for authentication
     */
    @JvmStatic
    suspend fun login(
        context: Context,
        appId: String?,
        externalId: String,
        jwtBearerToken: String? = null,
    ) {
        oneSignal.login(context, appId, externalId, jwtBearerToken)
    }

    /**
     * Logout the current user (suspend version).
     * Handles initialization automatically.
     *
     * @param context Application context is recommended for SDK operations
     * @param appId The OneSignal app ID
     */
    suspend fun logout(
        context: Context,
        appId: String?,
    ) {
        oneSignal.logout(context, appId)
    }

    /**
     * Used to retrieve services from the SDK when constructor dependency injection is not an
     * option.
     *
     * THIS IS AN INTERNAL INTERFACE AND SHOULD NOT BE USED DIRECTLY.
     */
    val services: IServiceProvider
        get() = oneSignal as IServiceProvider

    /**
     * Inline function to retrieve a specific service.
     *
     * THIS IS AN INTERNAL INTERFACE AND SHOULD NOT BE USED DIRECTLY.
     */
    inline fun <reified T : Any> getService(): T {
        return services.getService(T::class.java)
    }

    /**
     * Inline function to retrieve a specific service, or null if that service does not exist.
     *
     * THIS IS AN INTERNAL INTERFACE AND SHOULD NOT BE USED DIRECTLY.
     */
    inline fun <reified T : Any> getServiceOrNull(): T? {
        return services.getServiceOrNull(T::class.java)
    }
}
