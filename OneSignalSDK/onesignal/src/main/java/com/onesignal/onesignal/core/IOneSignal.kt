package com.onesignal.onesignal.core

import android.content.Context
import com.onesignal.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.INotificationsManager

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
    val user: IUserManager

    /**
     * The notification manager for accessing device-scoped
     * notification management.
     */
    val notifications: INotificationsManager

    /**
     * The location manager for accessing device-scoped
     * location management.
     */
    val location: ILocationManager

    /**
     * The In App Messaging manager for accessing device-scoped
     * IAP management.
     */
    val iam: IIAMManager

    /**
     * Access to debug the SDK in the additional information is required to diagnose any
     * SDK-related issues.
     *
     * WARNING: This should not be used in a production setting.
     */
    val debug: IDebugManager

    /**
     * Determines whether a user must consent to privacy prior
     * to their user data being sent up to OneSignal.  This
     * should be set to `true` prior to the invocation of
     * [initWithContext] to ensure compliance.
     */
    var requiresPrivacyConsent: Boolean

    /**
     * Indicates whether privacy consent has been granted. This field is only relevant when
     * the application has opted into data privacy protections. See [requiresPrivacyConsent].
     */
    var privacyConsent: Boolean

    /**
     * Initialize the OneSignal SDK.  This should be called during startup of the application.
     *
     * @param context The Android context the SDK should use.
     */
    fun initWithContext(context: Context)

    /**
     * Set the application ID the OneSignal SDK will be operating against.  This should be called
     * during startup of the application.  It can also be called again if the application ID needs
     * to be changed.  Changing the application ID will automatically log out any current user,
     * it's like starting over.
     *
     * @param appId The application ID the OneSignal SDK is bound to.
     */
    fun setAppId(appId: String)

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
    suspend fun login(externalId: String, externalIdHash: String? = null): IUserManager
    suspend fun login(externalId: String): IUserManager = login(externalId, null)

    /**
     * Log the SDK into OneSignal as a guest user. A guest user has no user identity that can later
     * be retrieved, except through this device as long as the app remains installed and the app
     * data is not cleared.
     */
    suspend fun loginGuest(): IUserManager

    /**
     * Set the optional user conflict resolver.  The [handler] will be called in the event user
     * operations were performed against a guest user, then [login] is called with an `externalId`
     * identifying an existing user.  When these sequence of events occur, the app developer must
     * provide insight into how to "merge" the two user states.  If no [IUserIdentityConflictResolver]
     * has been set here, the SDK will default to honoring the remote user.  This means any operations
     * made against the guest user prior to [login] will *not* be applied to the logged in user.
     */
    fun setUserConflictResolver(handler: IUserIdentityConflictResolver?)
    fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) // TODO: SHOULD INCLUDE?

    // TODO: Lifecycle events?
}
