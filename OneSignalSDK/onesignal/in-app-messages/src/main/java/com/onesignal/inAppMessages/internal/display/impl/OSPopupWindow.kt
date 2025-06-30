package com.onesignal.inAppMessages.internal.display.impl

import android.view.View
import android.widget.PopupWindow

/**
 * Custom PopupWindow to listen to dismiss event
 */
internal class OSPopupWindow(
    contentView: View?,
    width: Int,
    height: Int,
    focusable: Boolean,
    private val listener: PopupWindowListener,
) : PopupWindow(contentView, width, height, focusable) {
    /**
     * Used to differentiate when this popup window has been dismissed programmatically by OneSignal vs by the system.
     * When the back button is pressed while an IAM is displaying, the system will dismiss the popup window
     * without the SDK's awareness. We need to know of this event and run the post-dismissal flows.
     * Using this flag will prevent duplicate flows from triggering.
     */
    var wasDismissedManually: Boolean? = null

    internal interface PopupWindowListener {
        fun onDismiss(wasDismissedManually: Boolean?)
    }

    override fun dismiss() {
        super.dismiss()
        listener.onDismiss(wasDismissedManually)
    }
}
