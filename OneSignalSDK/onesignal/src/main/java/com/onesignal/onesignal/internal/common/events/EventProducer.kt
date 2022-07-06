package com.onesignal.onesignal.internal.common.events

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
        for(s in _subscribers) {
            callback(s)
        }
    }

    override fun fireThenRemove(callback: (THandler) -> Unit) {
        for(s in _subscribers) {
            _subscribers.remove(s)
            callback(s)
        }
    }
}
