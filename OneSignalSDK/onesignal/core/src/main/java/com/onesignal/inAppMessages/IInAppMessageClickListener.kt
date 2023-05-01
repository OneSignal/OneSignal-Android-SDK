package com.onesignal.inAppMessages

interface IInAppMessageClickListener {
    /**
     * Fires when a user clicks on a clickable element in the IAM.
     *
     * @param event The [IInAppMessageClickEvent] with the user's response and properties of this message.
     */
    fun onClick(event: IInAppMessageClickEvent)
}
