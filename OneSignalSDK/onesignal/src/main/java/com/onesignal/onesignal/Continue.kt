package com.onesignal.onesignal

import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A wrapper to allow Java invocations to Kotlin coroutines a little easier on the eye.
 */
object Continue {

    /**
     * Allows java code to provide a lambda as a continuation to a Kotlin coroutine.
     *
     * @param onFinished Called when the coroutine has completed, passing in the result of the coroutine.
     * @param context The optional coroutine context to run the [onFinished] lambda under. If not
     * specified an empty context will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun <R> with(onFinished: Consumer<Result<R>>, context: CoroutineContext = EmptyCoroutineContext): Continuation<R> {
        return object : Continuation<R> {
            override val context: CoroutineContext
                get() = context

            override fun resumeWith(result: Result<R>) {
                onFinished.accept(result)
            }
        }
    }
}