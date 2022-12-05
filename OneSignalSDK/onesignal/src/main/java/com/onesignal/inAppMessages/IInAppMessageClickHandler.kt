package com.onesignal.inAppMessages

interface IInAppMessageClickHandler {
    /**
     * Fires when a user clicks on a clickable element in the IAM.
     *
     * @param result The [IInAppMessageClickResult] with the user's response and properties of this message.
     */
    fun inAppMessageClicked(result: IInAppMessageClickResult)
}
