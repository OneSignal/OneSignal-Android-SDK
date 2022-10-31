package com.onesignal.iam.internal

import com.onesignal.iam.IIAMManager
import com.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.iam.IInAppMessageLifecycleHandler

internal class DummyIAMManager : IIAMManager {
    override var paused: Boolean = true

    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) {
    }

    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) {
    }
}
