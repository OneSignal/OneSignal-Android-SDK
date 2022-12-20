package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessage
import com.onesignal.inAppMessages.IInAppMessageAction
import com.onesignal.inAppMessages.IInAppMessageClickResult

class InAppMessageClickResult(
    override val message: IInAppMessage,

    override val action: IInAppMessageAction
) : IInAppMessageClickResult
