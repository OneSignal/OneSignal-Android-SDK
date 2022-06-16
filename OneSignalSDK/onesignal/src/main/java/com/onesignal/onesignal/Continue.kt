package com.onesignal.onesignal

import kotlinx.coroutines.Dispatchers
import java.util.function.Consumer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ContinueResult<R> (
    val isSuccess: Boolean,
    val data: R?,
    val throwable: Throwable?) {
}

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
    fun <R> with(onFinished: Consumer<ContinueResult<R>>, context: CoroutineContext = Dispatchers.Main): Continuation<R> {
        return object : Continuation<R> {
            override val context: CoroutineContext
                get() = context

            override fun resumeWith(result: Result<R>) {
                val data = ContinueResult<R>(result.isSuccess, result.getOrNull(), result.exceptionOrNull())
                onFinished.accept(data)
            }
        }
    }

    @JvmOverloads
    @JvmStatic
    fun <R> none(): Continuation<R> {
        return object : Continuation<R> {
            override val context: CoroutineContext
                get() = context

            override fun resumeWith(result: Result<R>) {
            }
        }
    }
}