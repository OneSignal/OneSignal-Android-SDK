package com.onesignal.onesignal.internal.common

/**
 * A generic interface which indicates the implementer has the ability
 * to notify handlers of any changes to the underlying state.
 */
open class BaseNotifyChanged<TChangedArgs> : INotifyChanged<INotifyChangedHandler<TChangedArgs>> {

    private val _subscribers: MutableList<INotifyChangedHandler<TChangedArgs>> = mutableListOf()

    override fun subscribe(handler: INotifyChangedHandler<TChangedArgs>) {
        _subscribers.add(handler)
    }

    override fun unsubscribe(handler: INotifyChangedHandler<TChangedArgs>) {
        _subscribers.remove(handler)
    }

    /**
     * Call this to notify all subscribers of a change.
     *
     * @param args The arguments that will be passed to each subscriber.
     */
    fun onChanged(args: TChangedArgs) {
        for(s in _subscribers) {
            s.onChanged(args)
        }
    }
}

interface INotifyChangedHandler<TChangedArgs> {
    fun onChanged(args: TChangedArgs)
}