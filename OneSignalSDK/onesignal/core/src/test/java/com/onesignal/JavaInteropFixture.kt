package com.onesignal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Suspending functions for the Java interop tests to call.
 *
 * These stand in for the SDK's public suspending API rather than mocking the bridge, so the Java
 * tests exercise the real thing a Java caller faces: a `Continuation` parameter appended to the
 * signature, and a return type of `Object`.
 *
 * [echo] and [boom] hop dispatchers, which is what every public suspending API in the SDK does
 * before it returns — see [returnsWithoutSuspending] for why that distinction matters.
 */
object JavaInteropFixture {
    /** Suspends, then completes with [value]. */
    @JvmStatic
    suspend fun echo(value: String): String = withContext(Dispatchers.Default) { value }

    /** Suspends, then fails. */
    @JvmStatic
    suspend fun boom(): String = withContext(Dispatchers.Default) { throw IllegalStateException("boom") }

    /** Suspends, then completes with no value, standing in for the `Unit`-returning APIs. */
    @JvmStatic
    suspend fun nothing() = withContext(Dispatchers.Default) { }

    /**
     * Completes without ever suspending.
     *
     * Kotlin compiles this to a plain return of the value, so the continuation passed to it is
     * never resumed. Every public suspending API in the SDK hops dispatchers and therefore does
     * suspend, but the distinction is load-bearing for anything built on continuation passing, so
     * the tests pin it rather than leaving it to be discovered.
     */
    @JvmStatic
    @Suppress("RedundantSuspendModifier")
    suspend fun returnsWithoutSuspending(): String = "immediate"
}
