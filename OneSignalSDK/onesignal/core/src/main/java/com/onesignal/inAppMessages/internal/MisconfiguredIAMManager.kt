package com.onesignal.inAppMessages.internal

import com.onesignal.inAppMessages.IInAppMessageClickHandler
import com.onesignal.inAppMessages.IInAppMessageLifecycleHandler
import com.onesignal.inAppMessages.IInAppMessagesManager

/**
 * The misconfigured IAMManager is an implementation of [IInAppMessagesManager] that warns the user they
 * have not included the appropriate IAM module.
 */
internal class MisconfiguredIAMManager : IInAppMessagesManager {
    override var paused: Boolean
        get() = throw EXCEPTION
        set(value) = throw EXCEPTION

    override fun addTrigger(key: String, value: String) = throw EXCEPTION
    override fun addTriggers(triggers: Map<String, String>) = throw EXCEPTION
    override fun removeTrigger(key: String) = throw EXCEPTION
    override fun removeTriggers(keys: Collection<String>) = throw EXCEPTION
    override fun clearTriggers() = throw EXCEPTION
    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) = throw EXCEPTION
    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) = throw EXCEPTION

    companion object {
        private val EXCEPTION: Throwable get() = Exception("Must include gradle module com.onesignal:InAppMessages in order to use this functionality!")
    }
}
