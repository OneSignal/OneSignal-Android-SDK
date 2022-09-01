package com.onesignal.core.internal.common.events

/**
 * A generic interface which indicates the implementer has the ability to callback through the
 * generic [THandler] interface specified.  When implemented, any outside component may set
 * themselves to be the callback.
 *
 * Unlike [IEventNotifier], there can only be one (1) callback at any given time.
 */
interface ICallbackNotifier<THandler> {
    /**
     * Whether there is a callback currently set.
     */
    val hasCallback: Boolean

    /**
     * Set the callback.
     *
     * @param handler The handler that will be called when required. Provide null to remove the callback.
     */
    fun set(handler: THandler?)
}

/**
 * An extension of [ICallbackNotifier] should be used internally to indicate the implementing
 * class is the producer of the callback.  The interface exists for abstraction/testing purposes.
 */
interface ICallbackProducer<THandler> : ICallbackNotifier<THandler> {
    fun fire(callback: (THandler) -> Unit)
}
