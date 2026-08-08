package com.onesignal

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import java.util.function.Consumer
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * Receives the outcome of a coroutine on behalf of a Java caller.
 *
 * A Kotlin `fun interface` rather than a `java.util.function.Consumer` so that it is usable on the
 * SDK's whole supported range. `Consumer` only exists from API 24, which is what confines
 * [Continue.with] to `@RequiresApi(N)`; this one is a plain interface and works from API 21. Java
 * still writes it as a lambda either way.
 */
fun interface ContinueCallback<R> {
    /**
     * Called once the coroutine has completed, whether it succeeded or failed.
     *
     * @param result The outcome of the coroutine, to continue processing from.
     */
    fun onFinished(result: ContinueResult<R>)
}

/**
 * The result provided by [Continue.with] when the Java user wants to inspect the results
 * of a Kotlin coroutine completing.
 */
class ContinueResult<R>(
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
    val throwable: Throwable?,
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
    @RequiresApi(Build.VERSION_CODES.N)
    @JvmOverloads
    @JvmStatic
    fun <R> with(
        onFinished: Consumer<ContinueResult<R>>,
        context: CoroutineContext = Dispatchers.Main,
    ): Continuation<R> {
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
     * The same callback bridge as [with], usable on every API level the SDK supports.
     *
     * ```java
     * OneSignal.getConsentGivenSuspend(Continue.callback(r -> {
     *     if (r.isSuccess()) render(r.getData());
     *     else Log.e("app", "could not read consent", r.getThrowable());
     * }));
     * ```
     *
     * Prefer this over [with]: the two behave identically, but [with] takes a
     * `java.util.function.Consumer`, which does not exist below API 24 and is not desugared here, so
     * on API 21 through 23 it fails at runtime rather than at compile time.
     *
     * @param onFinished Called when the coroutine has completed, with its [ContinueResult].
     * @param context The optional coroutine context to run [onFinished] under. Defaults to the main
     * thread, matching [with].
     */
    @JvmOverloads
    @JvmStatic
    fun <R> callback(
        onFinished: ContinueCallback<R>,
        context: CoroutineContext = Dispatchers.Main,
    ): Continuation<R> {
        return object : Continuation<R> {
            override val context: CoroutineContext
                get() = context

            override fun resumeWith(result: Result<R>) {
                onFinished.onFinished(ContinueResult(result.isSuccess, result.getOrNull(), result.exceptionOrNull()))
            }
        }
    }

    /**
     * Bridges a suspending call into a [Future], for Java callers that would rather collect the
     * answer than be called back.
     *
     * ```java
     * FutureContinuation<Boolean> consent = Continue.future();
     * OneSignal.getConsentGivenSuspend(consent);
     * boolean given = consent.get();  // off the main thread
     * ```
     *
     * Each instance backs one call. [FutureContinuation.get] refuses to run on the main thread, so
     * reach for [callback] when the answer is needed there.
     *
     * Unlike [with] and [callback] this takes no context, because there would be nothing for it to
     * govern — see [FutureContinuation.context].
     */
    @JvmStatic
    fun <R> future(): FutureContinuation<R> = FutureContinuation()

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
