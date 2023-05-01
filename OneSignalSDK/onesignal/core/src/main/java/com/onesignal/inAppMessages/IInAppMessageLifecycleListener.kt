package com.onesignal.inAppMessages

/**
 * The lifecycle handler interface that should be implemented and provided as input
 * to [IInAppMessagesManager.addLifecycleListener].  This allows for the app developer
 * to gain insight during the IAM lifecycle process.
 */
interface IInAppMessageLifecycleListener {
    /**
     * Called before an IAM is about to display to the user.
     *
     * @param event The IAM will display event information.
     */
    fun onWillDisplay(event: IInAppMessageWillDisplayEvent)

    /**
     * Called after an IAM has been displayed to the user.
     *
     * @param event The IAM did display event information.
     */
    fun onDidDisplay(event: IInAppMessageDidDisplayEvent)

    /**
     * Called when the IAM is about to dismiss.
     *
     * @param event The IAM will dismiss event information.
     */
    fun onWillDismiss(event: IInAppMessageWillDismissEvent)

    /**
     * Called after an IAM has been dismissed.
     *
     * @param event The IAM did dismiss event information.
     */
    fun onDidDismiss(event: IInAppMessageDidDismissEvent)
}
