package com.onesignal.onesignal.iam

/**
 * The lifecycle handler interface that should be implemented and provided as input
 * to [IIAMManager.setInAppMessageLifecycleHandler].  This allows for the app developer
 * to gain insight during the IAM lifecycle process.
 */
interface IInAppMessageLifecycleHandler {
    /**
     * Called before an IAM is about to display to the user.
     *
     * @param message The [IInAppMessage] that is about to be displayed.
     */
    fun onWillDisplayInAppMessage(message: IInAppMessage)

    /**
     * Called after an IAM has been displayed to the user.
     *
     * @param message The [IInAppMessage] that has been displayed.
     */
    fun onDidDisplayInAppMessage(message: IInAppMessage)

    /**
     * Called when the IAM is about to dismiss.
     *
     * @param message The [IInAppMessage] that is about to be dismissed.
     */
    fun onWillDismissInAppMessage(message: IInAppMessage)

    /**
     * Called after an IAM has been dismissed.
     *
     * @param message The [IInAppMessage] that has been dismissed.
     */
    fun onDidDismissInAppMessage(message: IInAppMessage)
}