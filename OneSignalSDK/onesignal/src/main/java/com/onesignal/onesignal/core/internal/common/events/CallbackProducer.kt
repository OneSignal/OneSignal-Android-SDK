package com.onesignal.onesignal.core.internal.common.events

/**
 * A standard implementation that implements [ICallbackProducer] to make callbacks less burdensome
 * to the user.
 */
open class CallbackProducer<THandler>() : ICallbackProducer<THandler> {

    private var _callback: THandler? = null

    override val hasCallback: Boolean
        get() = _callback != null

    override fun set(handler: THandler?) {
        _callback = handler
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handler if one exists. It is done this way to avoid this abstract class from
     * knowing the specific signature of the handler.
     *
     * @param callback The callback will be invoked if one exists, allowing you to call the handler.
     */
    override fun fire(callback: (THandler) -> Unit) {
        if (_callback != null)
        {
            callback(_callback!!)
        }
    }
}
