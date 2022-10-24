package com.onesignal.iam.internal

import com.onesignal.iam.IIAMManager
import com.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.iam.IInAppMessageLifecycleHandler

/**
 * The misconfigured IAMManager is an implementation of [IIAMManager] that warns the user they
 * have not included the appropriate IAM module.
 */
internal class MisconfiguredIAMManager : IIAMManager {
    override var paused: Boolean
        get() = throw EXCEPTION
        set(value) = throw EXCEPTION
    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) = throw EXCEPTION
    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) = throw EXCEPTION

    companion object {
        private val EXCEPTION: Throwable get() = Exception("Must include gradle module com.onesignal:IAM in order to use this functionality!")
    }
}
