package com.onesignal.inAppMessages

/**
 * An IAM action represents an action performed by the user in reaction to an IAM
 * being displayed.
 */
interface IInAppMessageAction {
    /**
     * An optional click name entered defined by the app developer when creating the IAM
     */
    val clickName: String?

    /**
     * Determines where the URL is opened, ie. Default browser.
     */
    val urlTarget: InAppMessageActionUrlType?

    /**
     * An optional URL that opens when the action takes place
     */
    val clickUrl: String?

    /**
     * Determines if this was the first action taken on the in app message
     */
    val isFirstClick: Boolean

    /**
     * Determines if tapping on the element should close the In-App Message.
     */
    val closesMessage: Boolean
}
