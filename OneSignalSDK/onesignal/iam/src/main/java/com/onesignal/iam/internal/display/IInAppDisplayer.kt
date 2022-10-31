package com.onesignal.iam.internal.display

import com.onesignal.iam.internal.InAppMessage

/**
 * Handles the displaying of an IAM on the device.
 */
internal interface IInAppDisplayer {

    /**
     * Displays the provided IAM on the device
     *
     * @param message The message that is to be displayed on the device.
     *
     * @return true if the message is displayed, false if the message fails to display, null if the message should be tried again.
     */
    suspend fun displayMessage(message: InAppMessage): Boolean?

    /**
     * Displayed the provided IAM on the device.
     *
     * @param previewUUID The ID of the preview IAM
     *
     * @return true if the message is displayed, false if the message fails to display.
     */
    suspend fun displayPreviewMessage(previewUUID: String): Boolean

    /**
     * Dismiss the currently displayed IAM on the device.
     */
    fun dismissCurrentInAppMessage()
}
