package com.onesignal

import android.content.Context
import com.onesignal.debug.IDebugManager
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager

interface IOneSignal {
    /**
     * The current SDK version as a string.
     */
    val sdkVersion: String

    /**
     * Whether the SDK has been initialized.
     */
    val isInitialized: Boolean

    /**
     * The user manager for accessing user-scoped
     * management.
     */
    val User: IUserManager

    /**
     * The session manager for accessing session-scoped management.
     */
    val Session: ISessionManager

    /**
     * The notification manager for accessing device-scoped
     * notification management.
     */
    val Notifications: INotificationsManager

    /**
     * The location manager for accessing device-scoped
     * location management.
     */
    val Location: ILocationManager

    /**
     * The In App Messaging manager for accessing device-scoped
     * IAP management.
     */
    val InAppMessages: IInAppMessagesManager

    /**
     * Access to debug the SDK in the event additional information is required to diagnose any
     * SDK-related issues.
     *
     * WARNING: This should not be used in a production setting.
     */
    val Debug: IDebugManager

    /**
     * Determines whether a user must consent to privacy prior
     * to their user data being sent up to OneSignal.  This
     * should be set to `true` prior to the invocation of
     * [initWithContext] to ensure compliance.
     */
    var consentRequired: Boolean

    /**
     * Indicates whether privacy consent has been granted. This field is only relevant when
     * the application has opted into data privacy protections. See [consentRequired].
     */
    var consentGiven: Boolean

    /**
     * Whether to disable the "GMS is missing" prompt to the user.
     */
    var disableGMSMissingPrompt: Boolean

    /**
     * Initialize the OneSignal SDK.  This should be called during startup of the application.
     *
     * @param context The Android context the SDK should use.
     * @param appId The application ID the OneSignal SDK is bound to.
     *
     * @return true if the SDK could be successfully initialized, false otherwise.
     */
    fun initWithContext(context: Context, appId: String?): Boolean

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
    fun login(externalId: String, jwtBearerToken: String? = null)
    fun login(externalId: String) = login(externalId, null)

    /**
     * Logout the user previously logged in via [login]. The [User] property now references
     * a new device-scoped user. A device-scoped user has no user identity that can later
     * be retrieved, except through this device as long as the app remains installed and the app
     * data is not cleared.
     */
    fun logout()
}
