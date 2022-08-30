package com.onesignal.onesignal.iam

interface IInAppMessageClickHandler {
    /**
     * Fires when a user taps on a clickable element in the notification such as a button or image
     *
     * @param result The [IInAppMessageAction] that should be taken based on the click.
     */
    fun inAppMessageClicked(result: IInAppMessageAction?)
}
