package com.onesignal.onesignal.internal.iam

import com.onesignal.onesignal.iam.IIAMManager
import com.onesignal.onesignal.iam.IInAppMessageClickHandler
import com.onesignal.onesignal.iam.IInAppMessageLifecycleHandler
import com.onesignal.onesignal.internal.common.events.CallbackProducer
import com.onesignal.onesignal.internal.common.events.ICallbackProducer
import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging

class IAMManager (
    private val _lifecycleCallback: ICallbackProducer<IInAppMessageLifecycleHandler> = CallbackProducer(),
    private val _messageClickCallback: ICallbackProducer<IInAppMessageClickHandler> = CallbackProducer()
        ) : IIAMManager {

    private var _paused: Boolean = true

    override var paused: Boolean
        get() = _paused
        set(value) {
            Logging.log(LogLevel.DEBUG, "setPaused(value: $value)")
            _paused = value
        }

    override fun setInAppMessageLifecycleHandler(handler: IInAppMessageLifecycleHandler?) {
        Logging.log(LogLevel.DEBUG, "setInAppMessageLifecycleHandler(handler: $handler)")
        _lifecycleCallback.set(handler)
    }

    override fun setInAppMessageClickHandler(handler: IInAppMessageClickHandler?) {
        Logging.log(LogLevel.DEBUG, "setInAppMessageClickHandler(handler: $handler)")
        _messageClickCallback.set(handler)
    }
}
