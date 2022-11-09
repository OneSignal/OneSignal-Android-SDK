package com.onesignal.inAppMessages.internal.state

import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePrompt

internal class InAppStateService {
    /**
     * Whether IAM is currently paused (no IAMs are shown).
     */
    var paused: Boolean = false

    /**
     * The message ID of the current IAM showing to the user. When null, no message is showing.
     */
    var inAppMessageIdShowing: String? = null

    /**
     * The last time an IAM was dismissed by the user.
     */
    var lastTimeInAppDismissed: Long? = null
    var currentPrompt: InAppMessagePrompt? = null
}
