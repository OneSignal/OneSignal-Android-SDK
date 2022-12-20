package com.onesignal.inAppMessages

/**
 * The data provided to [IInAppMessageClickHandler.inAppMessageClicked] when an IAM
 * has been clicked by the user.
 */
interface IInAppMessageClickResult {
    /**
     * The IAM that was clicked by the user.
     */
    val message: IInAppMessage

    /**
     * The action the user took to open the message.
     */
    val action: IInAppMessageAction
}
