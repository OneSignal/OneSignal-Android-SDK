package com.onesignal.onesignal.internal.iam

import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.onesignal.iam.IInAppMessageLifecycleHandler

class IAMManager : IIAMManager {

    /**
     * Whether the In-app messaging is currently paused.
     */
    override var paused: Boolean = true

    override fun setInAppMessageLifecycleHandler(callback: IInAppMessageLifecycleHandler) {

    }

    override fun setInAppMessageClickHandler(callback: IInAppMessageClickHandler) {

    }
}
