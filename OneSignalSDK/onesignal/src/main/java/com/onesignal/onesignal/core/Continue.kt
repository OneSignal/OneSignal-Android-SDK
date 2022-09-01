package com.onesignal.onesignal.core

import kotlinx.coroutines.Dispatchers
import java.util.function.Consumer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

class ContinueResult<R> (
    /**
     * Whether the coroutine call was successful (`true`) or not (`false`)
     */
    val isSuccess: Boolean,

    /**
     * The data that is returned by the coroutine when complete.  This will be `null` if [isSuccess]
     * is `false`.
     */
    val data: R?,

    /**
     * The throwable that was thrown by the coroutine.  This will be `null` if [isSuccess] is `true`.
     */
    val throwable: Throwable?
)

/**
 * A static helper class allowing Java invocations to Kotlin coroutines a little easier on the eye.
 * When invoking a suspending function in Java there is an extra parameter on the signature accepting
 * a [Continuation].  Typically this would require creating an anonymous object to implement both
 * [Continuation.context] and [Continuation.resumeWith].  This class allows you to accomplish the
 * same thing with a more inline/lambda approach:
 *
 * ```
 * someSuspendingMethod(normalArg1, normalArg2, Continue.with(result -> { ... }))
 * ```
 *
 * if you don't need to continue with anything you can simply use:
 *
 * ```
 * someSuspendingMethod(normalArg1, normalArg2, Continue.none())
 * ```
 */
object Continue {

    /**
     * Allows java code to provide a lambda as a continuation to a Kotlin coroutine.
     *
     * @param onFinished Called when the coroutine has completed, passing in the result ([ContinueResult])
     * of the coroutine for the java code to continue processing.
     * @param context The optional coroutine context to run the [onFinished] lambda under. If not
     * specified a context confined to the main thread will be used.
     *
     * @return The [Continuation] which should be provided to the Kotlin coroutine, and will be executed
     * once that coroutine has completed.
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

    /**
     * Allows java code to indicate they have no follow-up to a Kotlin coroutine.
     */
    @JvmOverloads
    @JvmStatic
    fun <R> none(): Continuation<R> {
        return object : Continuation<R> {
            override val context: CoroutineContext
                get() = Dispatchers.Main

            override fun resumeWith(result: Result<R>) {
            }
        }
    }
}
