package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class InitGateTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    // Runs isReady on its own thread; the returned latch opens when it returns.
    fun isReadyOnThread(
        gate: InitGate,
        result: AtomicReference<Boolean?>,
    ): CountDownLatch {
        val finished = CountDownLatch(1)
        Thread {
            result.set(gate.isReady(SdkOperation.LOGIN))
            finished.countDown()
        }.apply { isDaemon = true }.start()
        return finished
    }

    test("isReady is true at SUCCESS and false at NOT_STARTED and FAILED") {
        val gate = InitGate()
        gate.isReady(SdkOperation.LOGIN) shouldBe false

        gate.tryStart() shouldBe true
        gate.complete(InitState.FAILED)
        gate.isReady(SdkOperation.LOGIN) shouldBe false

        gate.tryStart() shouldBe true
        gate.complete(InitState.SUCCESS)
        gate.isReady(SdkOperation.LOGIN) shouldBe true
    }

    test("tryStart refuses while an attempt is running or after success") {
        val gate = InitGate()
        gate.tryStart() shouldBe true
        gate.tryStart() shouldBe false
        gate.complete(InitState.SUCCESS)
        gate.tryStart() shouldBe false
    }

    test("isReady at IN_PROGRESS waits, then reports the attempt's outcome") {
        for (outcome in listOf(InitState.SUCCESS, InitState.FAILED)) {
            val gate = InitGate()
            gate.tryStart()
            val result = AtomicReference<Boolean?>(null)
            val finished = isReadyOnThread(gate, result)

            finished.await(200, TimeUnit.MILLISECONDS) shouldBe false
            gate.complete(outcome)

            finished.await(2, TimeUnit.SECONDS) shouldBe true
            result.get() shouldBe (outcome == InitState.SUCCESS)
        }
    }

    test("a waiter follows a retry that starts before it wakes up") {
        val gate = InitGate()
        gate.tryStart()
        val result = AtomicReference<Boolean?>(null)
        val finished = isReadyOnThread(gate, result)
        finished.await(200, TimeUnit.MILLISECONDS) shouldBe false

        // Holding the gate's lock makes fail-then-retry atomic: the waiter can only see the retry.
        val lock = InitGate::class.java.getDeclaredField("lock").apply { isAccessible = true }.get(gate)
        synchronized(lock) {
            gate.complete(InitState.FAILED)
            gate.tryStart() shouldBe true
        }
        finished.await(200, TimeUnit.MILLISECONDS) shouldBe false

        gate.complete(InitState.SUCCESS)
        finished.await(2, TimeUnit.SECONDS) shouldBe true
        result.get() shouldBe true
    }

    test("notReadyMessage distinguishes a failed init from one that never started") {
        val gate = InitGate()
        gate.notReadyMessage("login") shouldBe "'login' ignored: call initWithContext with a valid appId first."
        gate.tryStart()
        gate.complete(InitState.FAILED)
        gate.notReadyMessage("login") shouldBe "'login' ignored: initWithContext failed."
    }
})
