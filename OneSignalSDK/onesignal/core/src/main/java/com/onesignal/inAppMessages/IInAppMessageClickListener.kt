package com.onesignal.inAppMessages

/**
 * An interface used to process a OneSignal In-App Message the user just clicked on.
 * Implement this interface and provide an instance to [IInAppMessagesManager.addClickListener]
 * in order to receive control when an IAM is clicked by the user.
 *
 * @see [In-App Messages | OneSignal Docs](https://documentation.onesignal.com/docs/in-app-messages)
 */
interface IInAppMessageClickListener {
    /**
     * Fires when a user clicks on a clickable element in the IAM.
     *
     * @param event The [IInAppMessageClickEvent] with the user's response and properties of this message.
     */
    fun onClick(event: IInAppMessageClickEvent)
}
