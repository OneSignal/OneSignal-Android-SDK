package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.IInAppMessagesManager

internal class DummyInAppMessagesManager : IInAppMessagesManager {
    override var paused: Boolean = true
    override fun addTrigger(key: String, value: String) {
    }

    override fun addTriggers(triggers: Map<String, String>) {
    }

    override fun removeTrigger(key: String) {
    }

    override fun removeTriggers(keys: Collection<String>) {
    }

    override fun clearTriggers() {
    }

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) {
    }

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) {
    }

    override fun addClickListener(listener: IInAppMessageClickListener) {
    }

    override fun removeClickListener(listener: IInAppMessageClickListener) {
    }
}
