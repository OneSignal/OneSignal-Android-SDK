package com.onesignal.common.events

/**
 * A generic interface which indicates the implementer has the ability to notify events through the
 * generic [THandler] interface specified.  When implemented, any outside component may subscribe
 * to the events being notified. When an event is to be raised, the implementor
 * will call a method within [THandler], the method(s) defined therein are entirely dependent on
 * the implementor/definition.
 *
 * Unlike [ICallbackNotifier], there can be one zero or more event subscribers at any given time.
 *
 * @param THandler The type that the implementor is expecting to raise events to.
 */
interface IEventNotifier<THandler> {

    /**
     * Subscribe to listen for events.
     *
     * @param handler The handler that will be called when the event(s) occur.
     */
    fun subscribe(handler: THandler)

    /**
     * Unsubscribe to no longer listen for events.
     *
     * @param handler The handler that was previous registered via [subscribe].
     */
    fun unsubscribe(handler: THandler)
}

/**
 * An extension of [IEventNotifier] should be used internally to indicate the implementing
 * class is the producer of the event.  The interface exists for abstraction/testing purposes.
 *
 * @param THandler The type that the implementor is expecting to raise events to.
 */
interface IEventProducer<THandler> : IEventNotifier<THandler> {
    /**
     * Call this to fire an event which will iterate through all subscribers
     * and allow the caller to drive the calling of the handler. It is done
     * this way to avoid this abstract class from knowing the specific
     * signature of the handler.
     *
     * @param callback The callback will be invoked for each subscriber, allowing you to call the handler.
     */
    fun fire(callback: (THandler) -> Unit)
}
