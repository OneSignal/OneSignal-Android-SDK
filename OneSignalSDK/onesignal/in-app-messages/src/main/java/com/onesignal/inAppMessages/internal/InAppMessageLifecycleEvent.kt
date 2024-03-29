package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessage
import com.onesignal.inAppMessages.IInAppMessageDidDismissEvent
import com.onesignal.inAppMessages.IInAppMessageDidDisplayEvent
import com.onesignal.inAppMessages.IInAppMessageWillDismissEvent
import com.onesignal.inAppMessages.IInAppMessageWillDisplayEvent

internal open class InAppMessageLifecycleEvent(override val message: IInAppMessage) : IInAppMessageWillDisplayEvent, IInAppMessageWillDismissEvent, IInAppMessageDidDisplayEvent, IInAppMessageDidDismissEvent
