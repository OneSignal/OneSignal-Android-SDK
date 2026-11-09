package com.onesignal

import android.content.Context
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.services.IServiceProvider
import com.onesignal.debug.IDebugManager
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.internal.OneSignalChoice
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
        get() = OneSignalChoice.isInitialized

    /**
     * The current SDK version as a string.
     */
    @JvmStatic
    val sdkVersion: String
        get() = OneSignalUtils.sdkVersion

    /**
     * The user manager, with a device-scoped user until (or if) [login] is called. Waits while init is running.
     * Before [initWithContext] succeeds this is a stand-in: observers are attached once init succeeds, other calls are logged and ignored.
     */
    @JvmStatic
    val User: IUserManager
        get() = OneSignalChoice.resolve().user

    /**
     * The session manager for accessing session-scoped management.
     * Before [initWithContext] succeeds this is a stand-in: outcome calls are logged and ignored.
     */
    @JvmStatic
    val Session: ISessionManager
        get() = OneSignalChoice.resolve().session

    /**
     * The notification manager. Waits while init is running. Before [initWithContext] succeeds this is a stand-in:
     * listeners and permission observers are attached once init succeeds, other calls are logged and ignored.
     */
    @JvmStatic
    val Notifications: INotificationsManager
        get() = OneSignalChoice.resolve().notifications

    /**
     * The location manager. Waits while init is running. Before [initWithContext] succeeds this is a stand-in:
     * a value set on `isShared` is applied once init succeeds, other calls are logged and ignored.
     */
    @JvmStatic
    val Location: ILocationManager
        get() = OneSignalChoice.resolve().location

    /**
     * The In App Messaging manager. Waits while init is running. Before [initWithContext] succeeds this is a stand-in:
     * listeners and `paused` are applied once init succeeds, other calls are logged and ignored.
     */
    @JvmStatic
    val InAppMessages: IInAppMessagesManager
        get() = OneSignalChoice.resolve().inAppMessages

    /**
     * Access to debug the SDK in the additional information is required to diagnose any
     * SDK-related issues.  Initialized immediately (can be used prior to [initWithContext]).
     *
     * WARNING: This should not be used in a production setting.
     */
    @JvmStatic
    val Debug: IDebugManager
        get() = OneSignalChoice.debug

    /**
     * Determines whether a user must consent to privacy prior
     * to their user data being sent up to OneSignal.  This
     * should be set to `true` prior to the invocation of
     * [initWithContext] to ensure compliance.
     */
    @JvmStatic
    var consentRequired: Boolean
        get() = OneSignalChoice.consentRequired
        set(value) {
            OneSignalChoice.consentRequired = value
        }

    /**
     * Indicates whether privacy consent has been granted. This field is only relevant when
     * the application has opted into data privacy protections. See [requiresPrivacyConsent].
     */
    @JvmStatic
    var consentGiven: Boolean
        get() = OneSignalChoice.consentGiven
        set(value) {
            OneSignalChoice.consentGiven = value
        }

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    @JvmStatic
    var disableGMSMissingPrompt: Boolean
        get() = OneSignalChoice.disableGMSMissingPrompt
        set(value) {
            OneSignalChoice.disableGMSMissingPrompt = value
        }

    /**
     * Initialize the OneSignal SDK.  This should be called during startup of the application.
     *
     * @param context The Android context the SDK should use. Required: a null context is logged and init does not start. The log says the SDK is not initialized.
     * @param appId Your OneSignal app ID. Always pass it: a null or invalid value falls back to the app ID
     * cached by an earlier launch, and with none cached init fails and logs that the SDK is not initialized. Check [isInitialized] for the outcome.
     */
    @JvmStatic
    fun initWithContext(
        context: Context?,
        appId: String?,
    ) {
        OneSignalChoice.resolve(context).initWithContext(context, appId)
    }

    /**
     * Initialize the OneSignal SDK asynchronously. This should be called during startup of the application.
     * This method provides a suspended version that returns a boolean indicating success.
     * Uses Dispatchers.IO internally to prevent ANRs and optimize for I/O operations.
     *
     * @param context Application context is recommended for SDK operations. Required: a null context is logged and init does not start. The log says the SDK is not initialized.
     * @param appId Your OneSignal app ID. Always pass it: a null or invalid value falls back to the app ID
     * cached by an earlier launch, and with none cached init fails and logs that the SDK is not initialized.
     * @return Boolean indicating if initialization was successful.
     */
    @JvmStatic
    suspend fun initWithContextSuspend(
        context: Context?,
        appId: String? = null,
    ): Boolean {
        return OneSignalChoice.resolve(context).initWithContextSuspend(context, appId)
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
        return OneSignalChoice.resolve().getUser()
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
        return OneSignalChoice.resolve().getSession()
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
        return OneSignalChoice.resolve().getNotifications()
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
        return OneSignalChoice.resolve().getLocation()
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
        return OneSignalChoice.resolve().getInAppMessages()
    }

    /**
     * Get the consent required flag in a thread-safe manner without blocking the calling thread.
     * Suspends until the SDK is initialized if initialization is in progress.
     * This is the suspend-safe version of the [consentRequired] property accessor.
     *
     * @return Whether a user must consent to privacy prior to their user data being sent to OneSignal.
     */
    @JvmStatic
    suspend fun getConsentRequiredSuspend(): Boolean = OneSignalChoice.getConsentRequired()

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
        OneSignalChoice.setConsentRequired(required)
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
        return OneSignalChoice.getConsentGiven()
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
        OneSignalChoice.setConsentGiven(value)
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
        return OneSignalChoice.getDisableGMSMissingPrompt()
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
        OneSignalChoice.setDisableGMSMissingPrompt(value)
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
     * @param externalId The external ID of the user to log in. Required: null or empty is ignored and logged. A null value's log says the SDK is not initialized when it is not.
     */
    @JvmStatic
    fun login(externalId: String?) = OneSignalChoice.resolve().login(externalId)

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
     * @param externalId The external ID of the user to log in. Required: null or empty is ignored and logged. A null value's log says the SDK is not initialized when it is not.
     * @param jwtBearerToken The optional JWT bearer token generated by your backend to establish
     * trust for the login operation.  Required when identity verification has been enabled. See
     * [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    @JvmStatic
    fun login(
        externalId: String?,
        jwtBearerToken: String? = null,
    ) = OneSignalChoice.resolve().login(externalId, jwtBearerToken)

    /**
     * Logout the user previously logged in via [login]. The [User] property now references
     * a new device-scoped user. A device-scoped user has no user identity that can later
     * be retrieved, except through this device as long as the app remains installed and the app
     * data is not cleared.
     */
    @JvmStatic
    fun logout() = OneSignalChoice.resolve().logout()

    /**
     * Update the JWT bearer token associated with [externalId]. Use this when your backend
     * has issued a new JWT for an already-logged-in user (e.g. in response to a previous
     * [IUserJwtInvalidatedListener.onUserJwtInvalidated] callback). Stores the JWT and
     * wakes the operation queue so any deferred ops can dispatch with the fresh token.
     *
     * @param externalId The external ID the JWT belongs to. Required: null or empty is ignored and logged. A null value's log says the SDK is not initialized when it is not.
     * @param token The new JWT bearer token issued by your backend. Must be non-empty; empty values are ignored and logged.
     */
    @JvmStatic
    fun updateUserJwt(
        externalId: String?,
        token: String,
    ) = OneSignalChoice.resolve().updateUserJwt(externalId, token)

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
        OneSignalChoice.resolve().addUserJwtInvalidatedListener(listener)

    /** Unsubscribe a listener previously registered via [addUserJwtInvalidatedListener]. */
    @JvmStatic
    fun removeUserJwtInvalidatedListener(listener: IUserJwtInvalidatedListener) =
        OneSignalChoice.resolve().removeUserJwtInvalidatedListener(listener)

    /**
     * Used to initialize the SDK when driven through user action. It is assumed [initWithContext]
     * has been called by the app developer, providing the appId, which has been cached for
     * this purpose.
     *
     * THIS IS AN INTERNAL INTERFACE AND SHOULD NOT BE USED DIRECTLY.
     */
    @JvmStatic
    suspend fun initWithContext(context: Context?): Boolean {
        return OneSignalChoice.resolve(context).initWithContext(context)
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
     * @param externalId The external ID of the user to log in. Required: null or empty is ignored and logged. A null value's log says the SDK is not initialized when it is not.
     * @param jwtBearerToken The optional JWT bearer token generated by your backend to establish
     * trust for the login operation.  Required when identity verification has been enabled. See
     * [Identity Verification | OneSignal](https://documentation.onesignal.com/docs/identity-verification)
     */
    @JvmStatic
    suspend fun loginSuspend(
        externalId: String?,
        jwtBearerToken: String? = null,
    ) {
        OneSignalChoice.resolve().loginSuspend(externalId, jwtBearerToken)
    }

    /**
     * Login with [externalId] plus [profile] as one Create User upsert (anonymous is not merged). Email/SMS create or transfer; a foreign alias fails it.
     * Same id is a no-op once a backend ID exists. Backend failure leaves this identity local; INVALID_ARGUMENT and NOT_INITIALIZED change nothing.
     * @param externalId Required: null or empty returns INVALID_ARGUMENT and changes nothing.
     */
    @JvmStatic
    @JvmOverloads
    suspend fun login(
        externalId: String?,
        profile: OneSignalUserProfile,
        jwtBearerToken: String? = null,
    ): OneSignalResult<LoginData> = OneSignalChoice.resolve().login(externalId, profile, jwtBearerToken)

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
        OneSignalChoice.resolve().logoutSuspend()
    }

    /**
     * Update the JWT bearer token associated with [externalId] without blocking the calling
     * thread. Suspend-safe version of [updateUserJwt].
     *
     * @param externalId The external ID the JWT belongs to. Required: null or empty is ignored and logged. A null value's log says the SDK is not initialized when it is not.
     * @param token The new JWT bearer token issued by your backend. Must be non-empty; empty values are ignored and logged.
     */
    @JvmStatic
    suspend fun updateUserJwtSuspend(
        externalId: String?,
        token: String,
    ) {
        OneSignalChoice.resolve().updateUserJwtSuspend(externalId, token)
    }

    /**
     * Used to retrieve services from the SDK when constructor dependency injection is not an
     * option.
     *
     * THIS IS AN INTERNAL INTERFACE AND SHOULD NOT BE USED DIRECTLY.
     */
    val services: IServiceProvider
        get() = OneSignalChoice.resolve() as IServiceProvider

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
