package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IIAMManager
import com.onesignal.inAppMessages.IInAppMessageClickHandler
import com.onesignal.inAppMessages.IInAppMessageLifecycleHandler

internal class DummyInAppMessagesManager : IIAMManager {
    override var paused: Boolean = true
    override fun addTrigger(key: String, value: Any) {
    }

    override fun addTriggers(triggers: Map<String, Any>) {
    }

    override fun removeTrigger(key: String) {
    }

    override fun removeTriggers(keys: Collection<String>) {
    }

    override fun clearTriggers() {
    }

    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) {
    }

    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) {
    }
}
