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
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend function getUserSuspend() instead.",
        replaceWith = ReplaceWith("getUserSuspend()"),
    )
    @Suppress("DEPRECATION")
    val User: IUserManager
        get() = oneSignal.user

    /**
     * The session manager for accessing session-scoped management.  Initialized only after [initWithContext]
     * has been called.
     */
    @JvmStatic
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend function getSessionSuspend() instead.",
        replaceWith = ReplaceWith("getSessionSuspend()"),
    )
    @Suppress("DEPRECATION")
    val Session: ISessionManager
        get() = oneSignal.session

    /**
     * The notification manager for accessing device-scoped notification management. Initialized
     * only after [initWithContext] has been called.
     */
    @JvmStatic
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend function getNotificationsSuspend() instead.",
        replaceWith = ReplaceWith("getNotificationsSuspend()"),
    )
    @Suppress("DEPRECATION")
    val Notifications: INotificationsManager
        get() = oneSignal.notifications

    /**
     * The location manager for accessing device-scoped location management. Initialized
     * only after [initWithContext] has been called.
     */
    @JvmStatic
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend function getLocationSuspend() instead.",
        replaceWith = ReplaceWith("getLocationSuspend()"),
    )
    @Suppress("DEPRECATION")
    val Location: ILocationManager
        get() = oneSignal.location

    /**
     * The In App Messaging manager for accessing device-scoped IAP management. Initialized
     * only after [initWithContext] has been called.
     */
    @JvmStatic
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend function getInAppMessagesSuspend() instead.",
        replaceWith = ReplaceWith("getInAppMessagesSuspend()"),
    )
    @Suppress("DEPRECATION")
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
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend functions getConsentRequiredSuspend() " +
            "and setConsentRequiredSuspend(required) instead.",
        replaceWith = ReplaceWith("getConsentRequiredSuspend()"),
    )
    @Suppress("DEPRECATION")
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
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend functions getConsentGivenSuspend() " +
            "and setConsentGivenSuspend(value) instead.",
        replaceWith = ReplaceWith("getConsentGivenSuspend()"),
    )
    @Suppress("DEPRECATION")
    var consentGiven: Boolean
        get() = oneSignal.consentGiven
        set(value) {
            oneSignal.consentGiven = value
        }

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    @JvmStatic
    @Deprecated(
        message =
        "Accessing this property may block the calling thread until the SDK is initialized and " +
            "cause ANRs when called on the main thread. Use the suspend functions getDisableGMSMissingPromptSuspend() " +
            "and setDisableGMSMissingPromptSuspend(value) instead.",
        replaceWith = ReplaceWith("getDisableGMSMissingPromptSuspend()"),
    )
    @Suppress("DEPRECATION")
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
    @Deprecated(
        message =
        "This blocking method may block the calling thread and cause ANRs when called on the " +
            "main thread. Use the suspend function initWithContextSuspend(context, appId) instead.",
        replaceWith = ReplaceWith("initWithContextSuspend(context, appId)"),
    )
    @Suppress("DEPRECATION")
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
     * Get the user manager without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [User] property accessor.
     *
     * @return The user manager for accessing user-scoped management.
     */
    @JvmStatic
    suspend fun getUserSuspend(): IUserManager {
        return oneSignal.getUser()
    }

    /**
     * Get the session manager without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [Session] property accessor.
     *
     * @return The session manager for accessing session-scoped management.
     */
    @JvmStatic
    suspend fun getSessionSuspend(): ISessionManager {
        return oneSignal.getSession()
    }

    /**
     * Get the notifications manager without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [Notifications] property accessor.
     *
     * @return The notification manager for accessing device-scoped notification management.
     */
    @JvmStatic
    suspend fun getNotificationsSuspend(): INotificationsManager {
        return oneSignal.getNotifications()
    }

    /**
     * Get the location manager without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [Location] property accessor.
     *
     * @return The location manager for accessing device-scoped location management.
     */
    @JvmStatic
    suspend fun getLocationSuspend(): ILocationManager {
        return oneSignal.getLocation()
    }

    /**
     * Get the in-app messages manager without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [InAppMessages] property accessor.
     *
     * @return The in-app messaging manager for accessing device-scoped IAM management.
     */
    @JvmStatic
    suspend fun getInAppMessagesSuspend(): IInAppMessagesManager {
        return oneSignal.getInAppMessages()
    }

    /**
     * Get the consent required flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [consentRequired] property accessor.
     *
     * @return Whether a user must consent to privacy prior to their user data being sent to OneSignal.
     */
    @JvmStatic
    suspend fun getConsentRequiredSuspend(): Boolean {
        return oneSignal.getConsentRequired()
    }

    /**
     * Set the consent required flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [consentRequired] property setter.
     *
     * @param required Whether a user must consent to privacy prior to their user data being sent to OneSignal.
     *                 Should be set to `true` prior to the invocation of [initWithContext] to ensure compliance.
     */
    @JvmStatic
    suspend fun setConsentRequiredSuspend(required: Boolean) {
        oneSignal.setConsentRequired(required)
    }

    /**
     * Get the consent given flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [consentGiven] property accessor.
     *
     * @return Whether privacy consent has been granted. This field is only relevant when
     *         the application has opted into data privacy protections. See [consentRequired].
     */
    @JvmStatic
    suspend fun getConsentGivenSuspend(): Boolean {
        return oneSignal.getConsentGiven()
    }

    /**
     * Set the consent given flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [consentGiven] property setter.
     *
     * @param value Whether privacy consent has been granted.
     */
    @JvmStatic
    suspend fun setConsentGivenSuspend(value: Boolean) {
        oneSignal.setConsentGiven(value)
    }

    /**
     * Get the disable GMS missing prompt flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [disableGMSMissingPrompt] property accessor.
     *
     * @return Whether to disable the "GMS is missing" prompt to the user.
     */
    @JvmStatic
    suspend fun getDisableGMSMissingPromptSuspend(): Boolean {
        return oneSignal.getDisableGMSMissingPrompt()
    }

    /**
     * Set the disable GMS missing prompt flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [disableGMSMissingPrompt] property setter.
     *
     * @param value Whether to disable the "GMS is missing" prompt to the user.
     */
    @JvmStatic
    suspend fun setDisableGMSMissingPromptSuspend(value: Boolean) {
        oneSignal.setDisableGMSMissingPrompt(value)
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
    @Deprecated(
        message =
        "This blocking method may block the calling thread and cause ANRs when called on the " +
            "main thread. Use the suspend function loginSuspend(externalId) instead.",
        replaceWith = ReplaceWith("loginSuspend(externalId)"),
    )
    @Suppress("DEPRECATION")
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
    @Deprecated(
        message =
        "This blocking method may block the calling thread and cause ANRs when called on the " +
            "main thread. Use the suspend function loginSuspend(externalId, jwtBearerToken) instead.",
        replaceWith = ReplaceWith("loginSuspend(externalId, jwtBearerToken)"),
    )
    @Suppress("DEPRECATION")
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
    @Deprecated(
        message =
        "This blocking method may block the calling thread and cause ANRs when called on the " +
            "main thread. Use the suspend function logoutSuspend() instead.",
        replaceWith = ReplaceWith("logoutSuspend()"),
    )
    @Suppress("DEPRECATION")
    fun logout() = oneSignal.logout()

    /**
     * Update the JWT bearer token associated with [externalId]. Use this when your backend
     * has issued a new JWT for an already-logged-in user (e.g. in response to a previous
     * [IUserJwtInvalidatedListener.onUserJwtInvalidated] callback). Stores the JWT and
     * wakes the operation queue so any deferred ops can dispatch with the fresh token.
     *
     * @param externalId The external ID the JWT belongs to.
     * @param token The new JWT bearer token issued by your backend.
     */
    @JvmStatic
    @Deprecated(
        message =
        "This blocking method may block the calling thread and cause ANRs when called on the " +
            "main thread. Use the suspend function updateUserJwtSuspend(externalId, token) instead.",
        replaceWith = ReplaceWith("updateUserJwtSuspend(externalId, token)"),
    )
    @Suppress("DEPRECATION")
    fun updateUserJwt(
        externalId: String,
        token: String,
    ) = oneSignal.updateUserJwt(externalId, token)

    /**
     * Subscribe a listener for JWT-invalidated events. Fires on a background thread when
     * the SDK detects that the stored JWT for a user is no longer valid (typically after
     * a 401 from the OneSignal backend). Apps should respond by fetching a fresh JWT from
     * their backend and supplying it via [updateUserJwt].
     *
     * Pure pub/sub: only listeners subscribed at the time of the invalidation receive the
     * event. Subscribe early (e.g. in `Application.onCreate`) to avoid missing events.
     */
    @JvmStatic
    fun addUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) =
        oneSignal.addUserJwtInvalidatedListener(listener)

    /** Unsubscribe a listener previously registered via [addUserJwtInvalidatedListener]. */
    @JvmStatic
    fun removeUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) =
        oneSignal.removeUserJwtInvalidatedListener(listener)

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
     * Login a user with external ID and optional JWT token without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [login] method.
     *
     * The act of logging a user into the OneSignal SDK will switch the [User] context to that specific user.
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
    suspend fun loginSuspend(
        externalId: String,
        jwtBearerToken: String? = null,
    ) {
        oneSignal.loginSuspend(externalId, jwtBearerToken)
    }

    /**
     * Logout the current user without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [logout] method.
     *
     * The [User] property now references a new device-scoped user. A device-scoped user has no
     * user identity that can later be retrieved, except through this device as long as the app
     * remains installed and the app data is not cleared.
     */
    @JvmStatic
    suspend fun logoutSuspend() {
        oneSignal.logoutSuspend()
    }

    /**
     * Update the JWT bearer token associated with [externalId] without blocking the calling
     * thread. Suspend-safe version of [updateUserJwt].
     *
     * @param externalId The external ID the JWT belongs to.
     * @param token The new JWT bearer token issued by your backend.
     */
    @JvmStatic
    suspend fun updateUserJwtSuspend(
        externalId: String,
        token: String,
    ) {
        oneSignal.updateUserJwtSuspend(externalId, token)
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
