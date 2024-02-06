package com.onesignal.common.threading

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking

/**
 * An abstraction which allows for a suspending function to coordinate
 * the completion of an event.
 */
class Waiter {
    private val channel = Channel<Any?>(Channel.CONFLATED)

    /**
     * Suspend the caller until [wake] has been called at least one time.
     */
    suspend fun waitForWake() = channel.receive()

    /**
     * Wake the suspending function that has called [waitForWake].
     */
    fun wake() = runBlocking { channel.send(null) }
}

/**
 * An abstraction which allows for a suspending function to coordinate
 * the completion of an event, where the event can pass data.
 */
open class WaiterWithValue<TType> {
    private val channel = Channel<TType>(Channel.CONFLATED)

    /**
     * Suspend the caller until [wake] has been called at least one time.
     *
     * @return the data provided by the caller of [wake].
     */
    suspend fun waitForWake(): TType = channel.receive()

    /**
     * Wake the suspending function that has called [waitForWake].
     *
     * @param value The data to be returned by the [waitForWake].
     */
    fun wake(value: TType) = runBlocking { channel.send(value) }
}
