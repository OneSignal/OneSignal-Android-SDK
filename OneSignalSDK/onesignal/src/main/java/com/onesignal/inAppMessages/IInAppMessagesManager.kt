package com.onesignal.inAppMessages

/**
 * The In App Message (IAM) Manager is a *device-scoped* manager for controlling the IAM
 * functionality within your application.  By default IAMs are enabled and will present
 * if the current user qualifies for any IAMs sent down by the OneSignal backend. To
 * blanket disable IAMs, set [paused] to `true` on startup.
 */
interface IInAppMessagesManager {

    /**
     * Whether the In-app messaging is currently paused.  When set to `true` no IAM
     * will be presented to the user regardless of whether they qualify for them. When
     * set to 'false` any IAMs the user qualifies for will be presented to the user
     * at the appropriate time.
     */
    var paused: Boolean

    /**
     * Add a trigger for the current user.  Triggers are currently explicitly used to determine
     * whether a specific IAM should be displayed to the user. See [Triggers | OneSignal](https://documentation.onesignal.com/docs/iam-triggers).
     *
     * If the trigger key already exists, it will be replaced with the value provided here. Note that
     * triggers are not persisted to the backend. They only exist on the local device and are applicable
     * to the current user.
     *
     * @param key The key of the trigger that is to be set.
     * @param value The value of the trigger. Although this is defined as [Any] its [Any.toString]
     * will be used for evaluation purposes.
     */
    fun addTrigger(key: String, value: Any)

    /**
     * Add multiple triggers for the current user.  Triggers are currently explicitly used to determine
     * whether a specific IAM should be displayed to the user. See [Triggers | OneSignal](https://documentation.onesignal.com/docs/iam-triggers).
     *
     * If the trigger key already exists, it will be replaced with the value provided here.  Note that
     * triggers are not persisted to the backend. They only exist on the local device and are applicable
     * to the current user.
     *
     * @param triggers The map of triggers that are to be added to the current user.  Although the
     * value of the [Map] is defined as [Any] its [Any.toString] will be used for evaluation
     * purposes.
     */
    fun addTriggers(triggers: Map<String, Any>)

    /**
     * Remove the trigger with the provided key from the current user.
     *
     * @param key The key of the trigger.
     */
    fun removeTrigger(key: String)

    /**
     * Remove multiple triggers from the current user.
     *
     * @param keys The collection of keys, all of which will be removed from the current user.
     */
    fun removeTriggers(keys: Collection<String>)

    /**
     * Clear all triggers from the current user.
     *
     * @return this user manager to allow for chaining of calls.
     */
    fun clearTriggers()

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
