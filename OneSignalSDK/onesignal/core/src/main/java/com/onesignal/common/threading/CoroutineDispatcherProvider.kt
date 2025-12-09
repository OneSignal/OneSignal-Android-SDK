package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/**
 * Provider interface for coroutine dispatchers.
 * This allows for proper dependency injection and easier testing.
 */
interface CoroutineDispatcherProvider {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher

    /**
     * Launch a coroutine on the IO dispatcher.
     */
    fun launchOnIO(block: suspend () -> Unit): Job

    /**
     * Launch a coroutine on the Default dispatcher.
     */
    fun launchOnDefault(block: suspend () -> Unit): Job
}
