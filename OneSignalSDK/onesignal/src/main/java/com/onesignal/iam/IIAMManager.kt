package com.onesignal.iam

/**
 * The In App Message (IAM) Manager is a *device-scoped* manager for controlling the IAM
 * functionality within your application.  By default IAMs are enabled and will present
 * if the current user qualifies for any IAMs sent down by the OneSignal backend. To
 * blanket disable IAMs, set [paused] to `true` on startup.
 */
interface IIAMManager {

    /**
     * Whether the In-app messaging is currently paused.  When set to `true` no IAM
     * will be presented to the user regardless of whether they qualify for them. When
     * set to 'false` any IAMs the user qualifies for will be presented to the user
     * at the appropriate time.
     */
    var paused: Boolean

    /**
     * Set the IAM lifecycle handler.
     *
     * @param handler: The handler that will be called at various times throughout
     *                 the IAM lifecycle.
     */
    fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?)

    /**
     * Set the IAM click handler.
     *
     * @param handler: The handler that will be called when the IAM has been
     *                 clicked.
     */
    fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?)
}
