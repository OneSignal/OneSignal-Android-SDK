package com.onesignal.inAppMessages.internal

import com.onesignal.core.internal.minification.KeepStub
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.IInAppMessagesManager

/**
 * The misconfigured IAMManager is an implementation of [IInAppMessagesManager]
 * that warns the dev they have not included the appropriate IAM module.
 */
@KeepStub
internal class MisconfiguredIAMManager : IInAppMessagesManager {
    override var paused: Boolean
        get() = throw EXCEPTION
        set(value) = throw EXCEPTION

    override fun addTrigger(
        key: String,
        value: String,
    ) = throw EXCEPTION

    override fun addTriggers(triggers: Map<String, String>) = throw EXCEPTION

    override fun removeTrigger(key: String) = throw EXCEPTION

    override fun removeTriggers(keys: Collection<String>) = throw EXCEPTION

    override fun clearTriggers() = throw EXCEPTION

    override fun addLifecycleListener(listener: IInAppMessageLifecycleListener) = throw EXCEPTION

    override fun removeLifecycleListener(listener: IInAppMessageLifecycleListener) = throw EXCEPTION

    override fun addClickListener(listener: IInAppMessageClickListener) = throw EXCEPTION

    override fun removeClickListener(listener: IInAppMessageClickListener) = throw EXCEPTION

    companion object {
        private val EXCEPTION: Throwable get() =
            Exception(
                "Must include gradle module com.onesignal:InAppMessages in order to use this functionality!",
            )
    }
}
