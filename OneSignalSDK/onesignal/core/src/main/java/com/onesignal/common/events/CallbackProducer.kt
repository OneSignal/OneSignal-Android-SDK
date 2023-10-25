package com.onesignal.common.events

import com.onesignal.common.threading.suspendifyOnMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A standard implementation that implements [ICallbackNotifier] and additional functionality to
 * make callbacks less burdensome to the user.
 */
open class CallbackProducer<THandler>() : ICallbackNotifier<THandler> {
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
    fun fire(callback: (THandler) -> Unit) {
        if (_callback != null) {
            callback(_callback!!)
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handler if one exists.  It is done this way to avoid this abstract class from
     * knowing the specific signature of the handler.  The callback will be invoked asynchronously
     * on the main thread.  Control will be returned immediately, most likely prior to the callbacks
     * being invoked.
     *
     * @param callback The callback will be invoked if one exists, allowing you to call the handler.
     */
    fun fireOnMain(callback: (THandler) -> Unit) {
        suspendifyOnMain {
            if (_callback != null) {
                callback(_callback!!)
            }
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handler if one exists. It is done this way to avoid this abstract class from
     * knowing the specific signature of the handler.
     *
     * @param callback The callback will be invoked if one exists, allowing you to call the handler.
     */
    suspend fun suspendingFire(callback: suspend (THandler) -> Unit) {
        if (_callback != null) {
            callback(_callback!!)
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handler if one exists. It is done this way to avoid this abstract class from
     * knowing the specific signature of the handler. The callback will be invoked on the main
     * thread.
     *
     * @param callback The callback will be invoked if one exists, allowing you to call the handler.
     */
    suspend fun suspendingFireOnMain(callback: suspend (THandler) -> Unit) {
        if (_callback != null) {
            withContext(Dispatchers.Main) {
                callback(_callback!!)
            }
        }
    }
}
