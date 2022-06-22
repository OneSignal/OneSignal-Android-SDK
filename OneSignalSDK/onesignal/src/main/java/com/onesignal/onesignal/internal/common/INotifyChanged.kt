package com.onesignal.onesignal.internal.common

/**
 * A generic interface which indicates the implementer has the ability
 * to notify handlers of any changes to the underlying state.
 */
interface INotifyChanged<THandler> {

    /**
     * Subscribe to listen for changes.
     *
     * @param handler The handler that will be called when varying events occur.
     */
    fun subscribe(handler: THandler)

    /**
     * Unsubscribe to no longer listen for changes.
     *
     * @param handler The handler that was previous registered via [subscribe].
     */
    fun unsubscribe(handler: THandler)
}