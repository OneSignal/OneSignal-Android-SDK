package com.onesignal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.onesignal.common.exceptions.MainThreadException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import kotlinx.coroutines.Dispatchers;

/**
 * Written in Java on purpose. The Kotlin tests cover what the helpers do; this file is the only
 * place that can show what a Java caller actually has to type, which is the whole point of the
 * helpers. If a signature stops being usable from Java, this file stops compiling.
 *
 * <p>Robolectric because the blocking guard reads the real Looper, and JUnit 4 because that is the
 * only runner Robolectric ships — the vintage engine bridges it onto the JUnit Platform that Kotest
 * uses for the rest of the module.
 */
@RunWith(RobolectricTestRunner.class)
public class ContinueJavaTest {

    /** Robolectric runs the test body on the main thread, so blocking has to happen off it. */
    private static <T> T offMainThread(java.util.concurrent.Callable<T> block) throws Exception {
        AtomicReference<Object> outcome = new AtomicReference<>();
        AtomicReference<Boolean> threw = new AtomicReference<>(false);
        Thread thread = new Thread(() -> {
            try {
                outcome.set(block.call());
            } catch (Throwable t) {
                threw.set(true);
                outcome.set(t);
            }
        });
        thread.start();
        thread.join(5_000);
        // Without this the helper would return null on a hung thread, which reads as a pass for any
        // test whose expected value happens to be null.
        assertFalse("the background thread never finished", thread.isAlive());

        if (threw.get()) {
            throw new ExecutionOnOtherThread((Throwable) outcome.get());
        }
        @SuppressWarnings("unchecked")
        T value = (T) outcome.get();
        return value;
    }

    private static final class ExecutionOnOtherThread extends RuntimeException {
        ExecutionOnOtherThread(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Collects a callback's single invocation. The fixture hops dispatchers, so the callback lands
     * on another thread — waiting on a latch keeps that deterministic instead of sleeping and hoping.
     */
    private static final class CallbackProbe<R> implements ContinueCallback<R> {
        private final java.util.concurrent.CountDownLatch fired =
                new java.util.concurrent.CountDownLatch(1);
        private final AtomicReference<ContinueResult<R>> result = new AtomicReference<>();

        @Override
        public void onFinished(ContinueResult<R> value) {
            result.set(value);
            fired.countDown();
        }

        ContinueResult<R> await() throws InterruptedException {
            assertTrue("callback never fired", fired.await(5, TimeUnit.SECONDS));
            return result.get();
        }
    }

    /**
     * Never executed — it exists so that javac checks the examples in the {@link Continue#future()}
     * and {@link Continue#callback} KDoc against the real public API. A doc comment is the one place
     * an unusable Java signature can sit indefinitely without anything noticing, so the examples are
     * repeated here where a compiler sees them.
     */
    @SuppressWarnings("unused")
    private static void documentedExamplesStillCompile() throws Exception {
        FutureContinuation<Boolean> consent = Continue.future();
        OneSignal.getConsentGivenSuspend(consent);
        boolean given = consent.get();

        OneSignal.getConsentGivenSuspend(Continue.callback(r -> {
            if (r.isSuccess()) {
                Boolean value = r.getData();
            } else {
                Throwable failure = r.getThrowable();
            }
        }));
    }

    @Test
    public void callbackReceivesTheValueAsALambda() throws Exception {
        CallbackProbe<String> probe = new CallbackProbe<>();

        // Written as a method reference to prove the interface is a usable SAM from Java.
        JavaInteropFixture.echo("os-1", Continue.callback(probe::onFinished, Dispatchers.getUnconfined()));

        ContinueResult<String> result = probe.await();
        assertTrue(result.isSuccess());
        assertEquals("os-1", result.getData());
        assertNull(result.getThrowable());
    }

    @Test
    public void callbackReportsAFailure() throws Exception {
        CallbackProbe<String> probe = new CallbackProbe<>();

        JavaInteropFixture.boom(Continue.callback(probe::onFinished, Dispatchers.getUnconfined()));

        ContinueResult<String> result = probe.await();
        assertFalse(result.isSuccess());
        assertNull(result.getData());
        assertEquals("boom", result.getThrowable().getMessage());
    }

    @Test
    public void futureHandsBackTheValue() throws Exception {
        FutureContinuation<String> future = Continue.future();

        JavaInteropFixture.echo("os-1", future);

        assertEquals("os-1", offMainThread(future::get));
    }

    @Test
    public void futureCompletesForAUnitReturningCall() throws Exception {
        FutureContinuation<kotlin.Unit> future = Continue.future();

        JavaInteropFixture.nothing(future);

        assertSame(kotlin.Unit.INSTANCE, offMainThread(future::get));
    }

    @Test
    public void futureReportsAFailureAsAnExecutionException() throws Exception {
        FutureContinuation<String> future = Continue.future();

        JavaInteropFixture.boom(future);

        try {
            offMainThread(future::get);
            fail("expected the failure to surface");
        } catch (ExecutionOnOtherThread wrapper) {
            ExecutionException thrown = (ExecutionException) wrapper.getCause();
            assertEquals("boom", thrown.getCause().getMessage());
        }
    }

    @Test
    public void blockingOnTheMainThreadIsRefused() {
        FutureContinuation<String> future = Continue.future();
        JavaInteropFixture.echo("os-1", future);

        try {
            future.get();
            fail("expected blocking on the main thread to be refused");
        } catch (Exception e) {
            assertTrue(
                    "expected MainThreadException but got " + e,
                    e instanceof MainThreadException);
        }
    }

    @Test
    public void futureTimesOutRatherThanBlockingForever() throws Exception {
        FutureContinuation<String> future = Continue.future();

        try {
            offMainThread(() -> future.get(50, TimeUnit.MILLISECONDS));
            fail("expected a timeout");
        } catch (ExecutionOnOtherThread wrapper) {
            assertTrue(wrapper.getCause() instanceof TimeoutException);
        }
        assertFalse(future.isDone());
    }

    /**
     * Pins the one shape this bridge cannot handle. A suspending function that returns without ever
     * suspending hands its value straight back and never resumes the continuation, so a Future
     * waiting on it would wait forever. Every public suspending API in the SDK hops dispatchers and
     * so does suspend; this test exists so that stops being an accident.
     */
    @Test
    public void aCallThatNeverSuspendsReturnsDirectlyAndLeavesTheFutureWaiting() {
        FutureContinuation<String> future = Continue.future();

        Object returnedDirectly = JavaInteropFixture.returnsWithoutSuspending(future);

        assertEquals("immediate", returnedDirectly);
        assertFalse("the continuation was never resumed", future.isDone());
    }
}
