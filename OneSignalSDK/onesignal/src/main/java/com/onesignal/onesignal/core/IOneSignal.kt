package com.onesignal.onesignal.core

import android.content.Context

import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.location.ILocationManager
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.core.user.Identity

interface IOneSignal {
    /**
     * The current SDK version as a string.
     */
    val sdkVersion: String

    /**
     * Whether the SDK is initialized.
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
     * Determines whether a user must consent to privacy prior
     * to their user data being sent up to OneSignal.  This
     * should be set to `true` prior to the invocation of
     * [initWithContext] to ensure compliance.
     */
    var requiresPrivacyConsent: Boolean

    /**
     * Initialize the OneSignal SDK.  This should be called during
     * startup of the application.
     *
     * @param context The Android context the SDK should use.
     */
    fun initWithContext(context: Context)

    /**
     * Set the application ID the OneSignal SDK will be operating
     * against.  This should be called during startup of the application.
     * It can also be called again if the application ID needs to be
     * changed.  Changing the application ID will automatically log out
     * any current user, it's like starting over.
     *
     * @param appId The application ID the OneSignal SDK is bound to.
     */
    suspend fun setAppId(appId: String)

    /**
     * Log into the provided identity.  The act of logging a user into the OneSignal SDK
     * will switch the [user] context to that specific user.
     *
     * The identity can either be:
     *
     * - Identity.Known:  An existing OneSignal user with known externalID (and optionally auth hash)
     * - Identity.Anonymous:  An unknown or not-yet-existing OneSignal user
     *      - You will get the same UserAnonymous if the active User was already Anonymous (TODO: Is this okay?)
     *
     * *Push Notifications and In App Messaging*
     * Logging in a new user will transfer push notification and in app messaging subscriptions
     * from the current user (if there is one) to the newly logged in user.  This is because
     * both Push and IAM are owned by the OS.
     * TODO: I feel like rather than transfer it should be a "pause".  I'm thinking of cases where
     *       a device has 2 different IDs and the person switches between them.  Should only the
     *       active user get push notifications or should both?  Should both have a *record* of
     *       them being enrolled in push on the same device, or just one at any given moment.
     *
     * TODO: IAM same story.  Although a user can only have 1 IAM subscription (a user could have
     *       multiple push subscriptions i.e. 2 phones).  If a user logs out their "IAM status"
     *       should stay....but this is only a thing if we want to provide the ability for the
     *       app developer to "pause" IAM at the user level, in addition to the device level.
     *       Use Case:  Allow the user to toggle on/off
     */
    suspend fun login(identity: Identity): IUserManager

    /**
     * Set the optional user conflict resolver.
     */
    fun setUserConflictResolver(handler: IUserIdentityConflictResolver?)
    fun setUserConflictResolver(handler: (local: IUserManager, remote: IUserManager) -> IUserManager) // TODO: SHOULD INCLUDE?

    // TODO: Lifecycle events?
}
