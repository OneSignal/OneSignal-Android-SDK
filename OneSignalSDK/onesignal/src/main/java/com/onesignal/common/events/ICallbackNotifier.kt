package com.onesignal.common.events

/**
 * A generic interface which indicates the implementer has the ability to callback through the
 * generic [THandler] interface specified.  When implemented, any outside component may set
 * themselves to be the callback via [set].  When the callback is to be called, the implementor
 * will call a method within [THandler], the method(s) defined therein are entirely dependent on
 * the implementor/definition.
 *
 * Unlike [IEventNotifier], there can only be one zero or one callbacks at any given time.
 *
 * @param THandler The type that the implementor is expecting to callback to.
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
 *
 * @param THandler The type that the implementor is expecting to callback to.
 */
interface ICallbackProducer<THandler> : ICallbackNotifier<THandler> {
    fun fire(callback: (THandler) -> Unit)
}
