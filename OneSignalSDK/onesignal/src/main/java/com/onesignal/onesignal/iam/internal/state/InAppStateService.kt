package com.onesignal.onesignal.iam.internal.state

import com.onesignal.onesignal.iam.internal.prompt.impl.InAppMessagePrompt
import java.util.*

internal class InAppStateService {
    var paused: Boolean = false
    var inAppMessageShowing = false
    var lastTimeInAppDismissed: Date? = null
    var currentPrompt: InAppMessagePrompt? = null
}
