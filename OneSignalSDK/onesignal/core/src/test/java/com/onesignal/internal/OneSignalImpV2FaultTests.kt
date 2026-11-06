package com.onesignal.internal

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.IOneSignal
import com.onesignal.OneSignalUserProfile
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessagesManager
import com.onesignal.location.ILocationManager
import com.onesignal.notifications.INotificationsManager
import com.onesignal.session.ISessionManager
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.subscriptions.IPushSubscription
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.callSuspend

/**
 * Fault injection against [OneSignalImpV2]: each test forces one thing the platform or the app can do to
 * the SDK (a full pool, a re-entrant or throwing listener, a throwing replay, a hammered API) and asserts no throw, no hang.
 */
@RobolectricTest
class OneSignalImpV2FaultTests : FunSpec({

    fun quietOneSignal() = OneSignalImpV2().also { Logging.logLevel = LogLevel.NONE }

    fun awaitTerminal(os: OneSignalImpV2, timeoutMs: Long = 5_000): InitState {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (os.gate.state == InitState.IN_PROGRESS && System.currentTimeMillis() < deadline) Thread.sleep(10)
        return os.gate.state
    }

    /** Runs [block] on a daemon thread and fails if it throws or is still running after [timeoutMs]. */
    fun <T> onDaemonThread(timeoutMs: Long = 5_000, block: () -> T): T {
        val result = AtomicReference<T>()
        val error = AtomicReference<Throwable?>(null)
        val thread = Thread { try { result.set(block()) } catch (t: Throwable) { error.set(t) } }
        thread.isDaemon = true
        thread.start()
        thread.join(timeoutMs)
        if (thread.isAlive) throw TimeoutException("still running after ${timeoutMs}ms")
        error.get()?.let { throw it }
        return result.get()
    }

    beforeAny { Logging.logLevel = LogLevel.NONE }

    afterAny {
        OneSignalDispatchers.resetForTest()
        val context = getApplicationContext<Context>()
        context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE).edit().clear().commit()
    }

    test("init dispatched onto a full IO pool ends FAILED instead of blocking every accessor forever") {
        OneSignalDispatchers.resetForTest()
        val ioExecutor = AtomicReference<ThreadPoolExecutor?>(null)
        OneSignalDispatchers.afterExecutorCreateForTest = { lane, executor -> if (lane == "IO") ioExecutor.set(executor) }

        val release = CountDownLatch(1)
        try {
            // 3 workers busy plus a 200-deep queue: the next dispatch is rejected, which cancels its job before it runs.
            repeat(203) { OneSignalDispatchers.launchOnIO { release.await(30, TimeUnit.SECONDS) } }
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline) {
                val pool = ioExecutor.get()
                if (pool != null && pool.activeCount == 3 && pool.queue.remainingCapacity() == 0) break
                Thread.sleep(10)
            }
            ioExecutor.get()!!.queue.remainingCapacity() shouldBe 0

            val os = quietOneSignal()
            val listener = ILogListener { if (it.entry.contains("could not be scheduled")) os.user.onesignalId }
            Logging.addListener(listener)
            try {
                os.initWithContext(getApplicationContext(), "appId")
                awaitTerminal(os) shouldBe InitState.FAILED
            } finally {
                Logging.removeListener(listener)
            }
            onDaemonThread { os.user.onesignalId } shouldBe ""
            os.isInitialized shouldBe false
        } finally {
            release.countDown()
        }

        // The pool drains, and a retry goes through.
        OneSignalDispatchers.resetForTest()
        val retry = quietOneSignal()
        retry.initWithContext(getApplicationContext(), "appId")
        awaitTerminal(retry) shouldBe InitState.SUCCESS
    }

    test("a log listener that touches the SDK while init is starting does not block the dispatch") {
        val os = quietOneSignal()
        val listener = ILogListener { if (it.entry.contains("ApplicationService.init")) os.user.onesignalId }
        Logging.addListener(listener)
        try {
            onDaemonThread { os.initWithContext(getApplicationContext(), "appId") }
            awaitTerminal(os) shouldBe InitState.SUCCESS
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("a LinkageError while starting the application service marks init FAILED") {
        val os = quietOneSignal()
        val listener =
            ILogListener {
                if (it.entry.contains("ApplicationService.init")) throw NoClassDefFoundError("missing")
            }
        Logging.addListener(listener)
        try {
            onDaemonThread { os.initWithContext(getApplicationContext(), "appId") } shouldBe false
            os.gate.state shouldBe InitState.FAILED
            onDaemonThread { os.user.onesignalId } shouldBe ""
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("a log listener that calls back into the SDK before init does not recurse until the stack overflows") {
        val os = quietOneSignal()
        val reentries = AtomicInteger()
        val listener =
            ILogListener {
                reentries.incrementAndGet()
                os.user.onesignalId
            }
        Logging.addListener(listener)
        try {
            onDaemonThread { os.user.externalId } shouldBe ""
            // One re-entry per log line is fine; unbounded re-entry is the StackOverflowError this guards.
            reentries.get() shouldBeLessThanOrEqual 4
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("a log listener that throws does not turn a dropped call into a crash") {
        val os = quietOneSignal()
        val listener = ILogListener { throw IllegalStateException("listener bug") }
        Logging.addListener(listener)
        try {
            onDaemonThread { os.user.externalId } shouldBe ""
            onDaemonThread { os.login("someone") }
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("held settings survive a failed init and are applied to the real manager when the retry succeeds") {
        // Core tests ship the Misconfigured managers, whose setters throw; that throw is the proof the
        // held value reached the real setter, and the replay must survive it.
        val context = getApplicationContext<Context>()
        val os = quietOneSignal()
        os.inAppMessages.paused = true
        os.location.isShared = true
        os.inAppMessages.paused shouldBe true
        os.location.isShared shouldBe true

        os.initWithContext(context, "")
        awaitTerminal(os) shouldBe InitState.FAILED
        os.inAppMessages.paused shouldBe true
        os.location.isShared shouldBe true

        val logs = ConcurrentLinkedQueue<String>()
        val listener = ILogListener { logs.add(it.entry) }
        Logging.addListener(listener)
        try {
            os.initWithContext(context, "appId")
            awaitTerminal(os) shouldBe InitState.SUCCESS
        } finally {
            Logging.removeListener(listener)
        }
        logs.count { it.contains("Replaying 'paused' threw") } shouldBe 1
        logs.count { it.contains("Replaying 'isShared' threw") } shouldBe 1
    }

    test("a replayed registration that throws does not fail init or lose the other registrations") {
        val context = getApplicationContext<Context>()
        val os = quietOneSignal()
        // Misconfigured IAM throws on addClickListener; the JWT listener replayed after it must still land.
        os.inAppMessages.addClickListener(mockkClass(com.onesignal.inAppMessages.IInAppMessageClickListener::class, relaxed = true))
        val fired = CountDownLatch(1)
        os.addUserJwtInvalidatedListener { fired.countDown() }

        os.initWithContext(context, "appId")
        awaitTerminal(os) shouldBe InitState.SUCCESS
        val store = os.getService(JwtTokenStore::class.java)
        store.putJwt("ext", "token")
        store.invalidateJwt("ext")
        fired.await(2, TimeUnit.SECONDS) shouldBe true
    }

    test("sixteen threads calling every public member through an in-flight init all return once it finishes") {
        val base = getApplicationContext<Context>()
        val holdInit = CountDownLatch(1)
        val initEntered = CountDownLatch(1)
        val blockingContext =
            object : ContextWrapper(base) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                    initEntered.countDown()
                    holdInit.await(30, TimeUnit.SECONDS)
                    return super.getSharedPreferences(name, mode)
                }
            }
        val os = quietOneSignal()
        os.initWithContext(blockingContext, "appId")
        initEntered.await(5, TimeUnit.SECONDS) shouldBe true
        os.gate.state shouldBe InitState.IN_PROGRESS

        val failures = ConcurrentLinkedQueue<String>()
        val inFlight = ConcurrentHashMap<Int, String>()
        val pool = Executors.newFixedThreadPool(16) { r -> Thread(r).also { it.isDaemon = true } }
        val go = CountDownLatch(1)
        val done = CountDownLatch(16)
        repeat(16) { n ->
            pool.execute {
                go.await()
                try {
                    hammer(os, n, failures) { inFlight[n] = it }
                } finally {
                    inFlight.remove(n)
                    done.countDown()
                }
            }
        }
        go.countDown()
        Thread.sleep(200)
        holdInit.countDown()

        val finished = done.await(20, TimeUnit.SECONDS)
        pool.shutdownNow()
        failures.toList().shouldBeEmpty()
        withClue("still running: ${inFlight.values.sorted()}") { finished shouldBe true }
        os.gate.state shouldBe InitState.SUCCESS
    }
})

private fun dummy(type: KType): Any? {
    if (type.isMarkedNullable) return null
    val kClass = type.classifier as KClass<*>
    return when {
        kClass == String::class -> "sample"
        kClass == Boolean::class -> false
        kClass == Int::class -> 0
        kClass == Float::class -> 0f
        kClass == Map::class -> emptyMap<String, String>()
        kClass == Collection::class -> emptyList<String>()
        kClass == OneSignalUserProfile::class -> OneSignalUserProfile.builder().build()
        kClass.java.isEnum -> kClass.java.enumConstants!!.first()
        else -> mockkClass(kClass, relaxed = true)
    }
}

private fun KCallable<*>.touchesInit() = parameters.any { it.type.classifier == Context::class }

private fun invoke(member: KCallable<*>, target: Any) {
    runBlocking {
        when (member) {
            is KProperty<*> -> {
                member.getter.call(target)
                if (member is KMutableProperty<*>) member.setter.call(target, dummy(member.returnType))
            }
            is KFunction<*> -> member.callSuspend(target, *member.parameters.drop(1).map { dummy(it.type) }.toTypedArray())
            else -> Unit
        }
        Unit
    }
}

// loginSuspend and the composite login await the backend round trip, which no unit test can answer.
private fun KCallable<*>.awaitsBackend() = name == "loginSuspend" || parameters.any { it.type.classifier == OneSignalUserProfile::class }

// Every member of every public surface, in a different order per thread, recording anything that throws.
private fun hammer(
    os: OneSignalImpV2,
    seed: Int,
    failures: ConcurrentLinkedQueue<String>,
    onCall: (String) -> Unit,
) {
    val surfaces: List<Pair<KClass<*>, () -> Any>> =
        listOf(
            IOneSignal::class to { os },
            IUserManager::class to { os.user },
            IPushSubscription::class to { os.user.pushSubscription },
            INotificationsManager::class to { os.notifications },
            IInAppMessagesManager::class to { os.inAppMessages },
            ILocationManager::class to { os.location },
            ISessionManager::class to { os.session },
        )
    val calls =
        surfaces.flatMap { (type, target) ->
            type.members
                .filter { it.name !in setOf("equals", "hashCode", "toString") && !it.touchesInit() && !it.awaitsBackend() }
                .map { member -> "${type.simpleName}.${member.name}" to { invoke(member, target()) } }
        }.shuffled(java.util.Random(seed.toLong()))
    for ((name, call) in calls) {
        onCall(name)
        try {
            call()
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            // The Misconfigured managers throw by design once init succeeds; that is the legacy contract.
            if (cause.message?.contains("Must include gradle module") == true) continue
            failures.add("$name threw $cause")
        }
    }
}
