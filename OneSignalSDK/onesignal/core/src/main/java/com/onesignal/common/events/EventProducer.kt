package com.onesignal.common.events

import com.onesignal.common.threading.suspendifyOnMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * A standard implementation that implements [IEventNotifier] and additional functionality to make
 * event firing less burdensome to the user.
 */
open class EventProducer<THandler> : IEventNotifier<THandler> {
    override val hasSubscribers: Boolean
        get() = subscribers.any()

    private val subscribers: MutableList<THandler> = Collections.synchronizedList(mutableListOf())

    override fun subscribe(handler: THandler) {
        subscribers.add(handler)
    }

    override fun unsubscribe(handler: THandler) {
        subscribers.remove(handler)
    }

    /**
     * Subscribe all from an existing producer to this subscriber.
     */
    fun subscribeAll(from: EventProducer<THandler>) {
        for (s in from.subscribers) {
            subscribe(s)
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handlers if there are any.
     *
     * @param callback The callback will be invoked for each subscribed handler, allowing you to call the handler.
     */
    fun fire(callback: (THandler) -> Unit) {
        val localList = subscribers.toList()
        for (s in localList) {
            callback(s)
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handlers if there are any.  The callback will be invoked asynchronously on the main
     * thread.  Control will be returned immediately, most likely prior to the callbacks being invoked.
     *
     * @param callback The callback will be invoked for each subscribed handler, allowing you to call the handler.
     */
    fun fireOnMain(callback: (THandler) -> Unit) {
        suspendifyOnMain {
            for (s in subscribers) {
                callback(s)
            }
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handlers if there are any.
     *
     * @param callback The callback will be invoked for each subscribed handler, allowing you to call the handler.
     */
    suspend fun suspendingFire(callback: suspend (THandler) -> Unit) {
        for (s in subscribers) {
            callback(s)
        }
    }

    /**
     * Call this to fire the callback which will allow the caller to drive the calling of the
     * callback handlers if there are any. The callback will be invoked on the main thread.
     *
     * @param callback The callback will be invoked for each subscribed handler, allowing you to call the handler.
     */
    suspend fun suspendingFireOnMain(callback: suspend (THandler) -> Unit) {
        withContext(Dispatchers.Main) {
            for (s in subscribers) {
                callback(s)
            }
        }
    }
}
