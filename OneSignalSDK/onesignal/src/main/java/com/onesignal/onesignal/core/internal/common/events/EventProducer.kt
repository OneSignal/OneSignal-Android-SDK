package com.onesignal.onesignal.core.internal.common.events

/**
 * A standard implementation that implements [IEventProducer] to make event firing less burdensome
 * to the user.
 */
open class EventProducer<THandler> : IEventProducer<THandler> {

    private val _subscribers: MutableList<THandler> = mutableListOf()

    override fun subscribe(handler: THandler) {
        _subscribers.add(handler)
    }

    override fun unsubscribe(handler: THandler) {
        _subscribers.remove(handler)
    }

    override fun fire(callback: (THandler) -> Unit) {
        for (s in _subscribers) {
            callback(s)
        }
    }

    /**
     * Conditional fire all subscribers *until one indicates to stop firing*
     *
     * @param callback The callback, returns true when no more subscribers should be called.
     *
     * @return Whether a subscriber callback indicated to stop firing.
     */
    fun conditionalFire(callback: (THandler) -> Boolean): Boolean {
        for (s in _subscribers) {
            if (!callback(s))
                return true
        }

        return true
    }

    suspend fun suspendingFire(callback: suspend (THandler) -> Unit) {
        for (s in _subscribers) {
            callback(s)
        }
    }
}
