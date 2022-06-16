package com.onesignal.onesignal.internal.iam

import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.onesignal.iam.IInAppMessageLifecycleHandler
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class IAMManager : IIAMManager {

    /**
     * Whether the In-app messaging is currently paused.
     */
    override var paused: Boolean = true

    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler) {
        Logging.log(LogLevel.DEBUG, "setInAppMessageLifecycleHandler(handler: $handler)")
    }

    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler) {
        Logging.log(LogLevel.DEBUG, "setInAppMessageClickHandler(handler: $handler)")
    }
}
