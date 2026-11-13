package com.onesignal.internal

import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
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

    test("a wait on the init thread while init is running returns false instead of waiting on itself") {
        val gate = InitGate()
        gate.tryStart()
        val onInitThread = AtomicReference<Boolean?>(null)
        val otherThread = AtomicReference<Boolean?>(null)

        val initThread =
            Thread {
                gate.runInit {
                    onInitThread.set(gate.isReady(SdkOperation.LOGIN))
                    val finished = isReadyOnThread(gate, otherThread)
                    finished.await(200, TimeUnit.MILLISECONDS) shouldBe false
                    gate.complete(InitState.SUCCESS)
                    finished.await(2, TimeUnit.SECONDS) shouldBe true
                    true
                }
            }.apply { isDaemon = true }
        initThread.start()
        initThread.join(5_000)

        initThread.isAlive shouldBe false
        onInitThread.get() shouldBe false
        otherThread.get() shouldBe true
    }

    test("a failed attempt finishing late does not clear the init thread a retry already claimed") {
        val gate = InitGate()
        val retryClaimed = CountDownLatch(1)
        val firstDone = CountDownLatch(1)
        val retryResult = AtomicReference<Boolean?>(null)
        val retry =
            Thread {
                gate.tryStart()
                gate.runInit {
                    retryClaimed.countDown()
                    firstDone.await(2, TimeUnit.SECONDS)
                    retryResult.set(gate.isReady(SdkOperation.LOGIN))
                    gate.complete(InitState.SUCCESS)
                    true
                }
            }.apply { isDaemon = true }

        gate.tryStart()
        val first =
            Thread {
                gate.runInit {
                    gate.complete(InitState.FAILED)
                    retry.start()
                    retryClaimed.await(2, TimeUnit.SECONDS)
                    false
                }
                firstDone.countDown()
            }.apply { isDaemon = true }
        first.start()
        first.join(2_000)
        retry.join(2_000)

        retry.isAlive shouldBe false
        retryResult.get() shouldBe false
        gate.isSuccess shouldBe true
    }

    test("calls before init are summarized in one error when init succeeds, and only once") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val gate = InitGate()
            gate.isReady(SdkOperation.LOGIN)
            gate.isReady(SdkOperation.LOGIN)
            gate.logQueued(SdkOperation.NOTIFICATIONS, "addClickListener")
            gate.isReady(SdkOperation.USER, "addTag")
            events.clear()

            gate.tryStart()
            gate.complete(InitState.SUCCESS)
            gate.complete(InitState.SUCCESS)

            val summaries = events.filter { it.level == LogLevel.ERROR }.map { it.entry.substringAfter("] ") }
            summaries shouldBe
                listOf("OneSignal calls made before initWithContext were ignored: login, User.addTag. Call initWithContext first.")
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("no summary is logged when nothing was called before init") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val gate = InitGate()
            gate.tryStart()
            gate.complete(InitState.SUCCESS)
            events.filter { it.level == LogLevel.ERROR } shouldBe emptyList()
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("calls before init are also summarized when the attempt fails") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val gate = InitGate()
            gate.isReady(SdkOperation.USER, "addTag")
            events.clear()

            gate.tryStart()
            gate.complete(InitState.FAILED)

            events.filter { it.level == LogLevel.ERROR }.map { it.entry.substringAfter("] ") } shouldBe
                listOf("OneSignal calls made before initWithContext were ignored: User.addTag. Call initWithContext first.")
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("a repeated ignored call logs ERROR once per attempt and DEBUG after that") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val gate = InitGate()
            repeat(3) { gate.isReady(SdkOperation.USER, "onesignalId") }

            val levels = events.filter { it.entry.contains("'User.onesignalId' ignored") }.map { it.level }
            levels shouldBe listOf(LogLevel.ERROR, LogLevel.DEBUG, LogLevel.DEBUG)
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("a new attempt logs a repeated ignored call at ERROR again") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val gate = InitGate()
            gate.isReady(SdkOperation.USER, "onesignalId")
            gate.tryStart()
            val initThread =
                Thread {
                    gate.runInit {
                        gate.isReady(SdkOperation.USER, "onesignalId")
                        gate.complete(InitState.SUCCESS)
                        true
                    }
                }.apply { isDaemon = true }
            initThread.start()
            initThread.join(2_000)

            val levels = events.filter { it.entry.contains("'User.onesignalId' ignored") }.map { it.level }
            levels shouldBe listOf(LogLevel.ERROR, LogLevel.ERROR)
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("isReady on an interrupted thread returns false without throwing and keeps the interrupt") {
        for (inProgress in listOf(false, true)) {
            val gate = InitGate()
            if (inProgress) gate.tryStart()
            val result = AtomicReference<Boolean?>(null)
            val stillInterrupted = AtomicReference<Boolean?>(null)
            val caller =
                Thread {
                    Thread.currentThread().interrupt()
                    result.set(gate.isReady(SdkOperation.USER, "addTag"))
                    stillInterrupted.set(Thread.currentThread().isInterrupted)
                }.apply { isDaemon = true }
            caller.start()
            caller.join(2_000)

            caller.isAlive shouldBe false
            result.get() shouldBe false
            stillInterrupted.get() shouldBe true
        }
    }

    test("an attempt whose block returns without completing is marked FAILED") {
        val gate = InitGate()
        gate.tryStart()
        gate.runInit { false } shouldBe false
        gate.state shouldBe InitState.FAILED
    }
})
