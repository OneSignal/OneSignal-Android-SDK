package com.onesignal.notifications.internal.ingress

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.google.common.util.concurrent.SettableFuture
import com.onesignal.OneSignal
import com.onesignal.notifications.internal.bundle.INotificationBundleProcessor
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RobolectricTest
class NotificationIngressTests : FunSpec({
    lateinit var context: Context
    lateinit var bundleProcessor: INotificationBundleProcessor

    beforeAny {
        context = ApplicationProvider.getApplicationContext()
        NotificationIngress.resetForTest(context)
        bundleProcessor = mockk(relaxed = true)
        mockkObject(OneSignal)
        coEvery { OneSignal.initWithContext(any()) } returns true
        every { OneSignal.getService<INotificationBundleProcessor>() } returns bundleProcessor
    }

    afterAny {
        unmockkObject(OneSignal)
        unmockkObject(OSWorkManagerHelper)
    }

    test("FCM input remains durable when drain scheduling fails") {
        NotificationIngress.drainSchedulerForTest = { throw IllegalStateException("scheduler unavailable") }
        val bundle =
            Bundle().apply {
                putString("custom", """{"i":"notification-id"}""")
                putString("alert", "message")
            }

        shouldThrow<IllegalStateException> {
            NotificationIngress.persistFcm(
                context,
                Intent("com.google.android.c2dm.intent.RECEIVE"),
                bundle,
            )
        }

        NotificationIngress.pendingCountForTest(context) shouldBe 1
    }

    test("duplicate FCM input replaces the same durable record") {
        NotificationIngress.drainSchedulerForTest = {}
        val bundle = Bundle().apply { putString("custom", """{"i":"notification-id"}""") }
        val intent = Intent("com.google.android.c2dm.intent.RECEIVE")

        NotificationIngress.persistFcm(context, intent, bundle)
        NotificationIngress.persistFcm(context, intent, bundle)

        NotificationIngress.pendingCountForTest(context) shouldBe 1
    }

    test("dismiss input is persisted before scheduling") {
        var countAtSchedule = 0
        NotificationIngress.drainSchedulerForTest = {
            countAtSchedule = NotificationIngress.pendingCountForTest(context)
        }
        val intent =
            Intent().apply {
                putExtra("androidNotificationId", 42)
                putExtra("dismissed", true)
            }

        NotificationIngress.persistDismiss(context, intent)

        countAtSchedule shouldBe 1
    }

    test("FCM handoff waits for WorkManager to persist the drain") {
        val workManager = mockk<WorkManager>()
        val operation = mockk<Operation>()
        val operationResult = SettableFuture.create<Operation.State.SUCCESS>()
        val enqueueCalled = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        mockkObject(OSWorkManagerHelper)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } answers {
            enqueueCalled.countDown()
            operation
        }
        every { operation.result } returns operationResult
        val bundle = Bundle().apply { putString("custom", """{"i":"notification-id"}""") }

        Thread {
            try {
                runBlocking { NotificationIngress.persistFcm(context, Intent(), bundle) }
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }.start()

        enqueueCalled.await(1, TimeUnit.SECONDS) shouldBe true
        completed.await(50, TimeUnit.MILLISECONDS) shouldBe false
        operationResult.set(Operation.SUCCESS)
        completed.await(1, TimeUnit.SECONDS) shouldBe true
        failure.get() shouldBe null
    }

    test("a pending drain enqueue does not hold the ingress thread") {
        val workManager = mockk<WorkManager>()
        val firstResult = SettableFuture.create<Operation.State.SUCCESS>()
        val secondResult = SettableFuture.create<Operation.State.SUCCESS>()
        val results = ArrayDeque(listOf(firstResult, secondResult))
        val secondEnqueued = CountDownLatch(1)
        mockkObject(OSWorkManagerHelper)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } answers {
            val result = results.removeFirst()
            if (result === secondResult) secondEnqueued.countDown()
            mockk<Operation> { every { this@mockk.result } returns result }
        }
        val executor = Executors.newSingleThreadExecutor()
        val ingressScope = CoroutineScope(executor.asCoroutineDispatcher())

        try {
            val first =
                ingressScope.launch {
                    NotificationIngress.persistFcm(context, Intent(), Bundle().apply { putString("custom", """{"i":"first"}""") })
                }
            val second =
                ingressScope.launch {
                    NotificationIngress.persistFcm(context, Intent(), Bundle().apply { putString("custom", """{"i":"second"}""") })
                }

            secondEnqueued.await(1, TimeUnit.SECONDS) shouldBe true
            first.isCompleted shouldBe false
            firstResult.set(Operation.SUCCESS)
            secondResult.set(Operation.SUCCESS)
            first.join()
            second.join()
        } finally {
            ingressScope.cancel()
            executor.shutdownNow()
        }
    }

    test("failed restore enqueue can be retried by a later boot handoff") {
        val workManager = mockk<WorkManager>()
        val failed = SettableFuture.create<Operation.State.SUCCESS>().apply { setException(IllegalStateException("db full")) }
        val succeeded = SettableFuture.create<Operation.State.SUCCESS>().apply { set(Operation.SUCCESS) }
        val results = ArrayDeque(listOf(failed, succeeded))
        mockkObject(OSWorkManagerHelper)
        every { OSWorkManagerHelper.getInstance(any()) } returns workManager
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } answers {
            val result = results.removeFirst()
            mockk<Operation> { every { this@mockk.result } returns result }
        }
        NotificationRestoreWorkManager.resetForTest()

        try {
            shouldThrow<IllegalStateException> { NotificationIngress.enqueueRestore(context) }
            NotificationIngress.enqueueRestore(context)

            verify(exactly = 2) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
        } finally {
            NotificationRestoreWorkManager.resetForTest()
        }
    }

    test("unknown record kind is discarded without blocking the drain") {
        NotificationIngress.putRawForTest(context, "unknown", "UNKNOWN", "{}")

        val result = NotificationIngressDrainWorker(context, mockk(relaxed = true)).doWork()

        result.javaClass shouldBe ListenableWorker.Result.success().javaClass
        NotificationIngress.pendingCountForTest(context) shouldBe 0
    }

    test("failed record is retried while later records continue") {
        NotificationIngress.drainSchedulerForTest = {}
        val badBundle = Bundle().apply { putString("custom", """{"i":"bad-id"}""") }
        val goodBundle = Bundle().apply { putString("custom", """{"i":"good-id"}""") }
        NotificationIngress.persistFcm(context, Intent(), badBundle)
        NotificationIngress.persistFcm(context, Intent(), goodBundle)
        every { bundleProcessor.processBundleFromReceiver(any(), any()) } answers {
            if (secondArg<Bundle>().getString("custom")!!.contains("bad-id")) {
                throw IllegalStateException("bad payload")
            }
            null
        }

        val result = NotificationIngressDrainWorker(context, mockk(relaxed = true)).doWork()

        result.javaClass shouldBe ListenableWorker.Result.retry().javaClass
        NotificationIngress.pendingCountForTest(context) shouldBe 1
        NotificationIngress.attemptCountForTest(context, "fcm:bad-id") shouldBe 1
    }

    test("record is dropped after the bounded retry limit") {
        NotificationIngress.drainSchedulerForTest = {}
        val bundle = Bundle().apply { putString("custom", """{"i":"bad-id"}""") }
        NotificationIngress.persistFcm(context, Intent(), bundle)
        every { bundleProcessor.processBundleFromReceiver(any(), any()) } throws IllegalStateException("bad payload")
        val workerParameters = mockk<WorkerParameters>(relaxed = true)
        val worker = NotificationIngressDrainWorker(context, workerParameters)

        repeat(NotificationIngressDrainWorker.MAX_RECORD_ATTEMPTS) { worker.doWork() }

        NotificationIngress.pendingCountForTest(context) shouldBe 0
    }

    test("duplicate input does not reset the record attempt count") {
        NotificationIngress.drainSchedulerForTest = {}
        val bundle = Bundle().apply { putString("custom", """{"i":"bad-id"}""") }
        NotificationIngress.persistFcm(context, Intent(), bundle)
        every { bundleProcessor.processBundleFromReceiver(any(), any()) } throws IllegalStateException("bad payload")
        NotificationIngressDrainWorker(context, mockk(relaxed = true)).doWork()

        NotificationIngress.persistFcm(context, Intent(), bundle)

        NotificationIngress.attemptCountForTest(context, "fcm:bad-id") shouldBe 1
    }

    test("expired record is discarded without processing") {
        NotificationIngress.putRawForTest(
            context,
            id = "expired",
            kind = "FCM",
            payload = "{}",
            createdAtMs = System.currentTimeMillis() - NotificationIngressDrainWorker.MAX_RECORD_AGE_MS,
        )

        NotificationIngressDrainWorker(context, mockk(relaxed = true)).doWork()

        NotificationIngress.pendingCountForTest(context) shouldBe 0
    }

    test("cancellation propagates without consuming a record attempt") {
        NotificationIngress.drainSchedulerForTest = {}
        val bundle = Bundle().apply { putString("custom", """{"i":"stopped-id"}""") }
        NotificationIngress.persistFcm(context, Intent(), bundle)
        every { bundleProcessor.processBundleFromReceiver(any(), any()) } throws CancellationException("stopped")

        shouldThrow<CancellationException> {
            NotificationIngressDrainWorker(context, mockk(relaxed = true)).doWork()
        }

        NotificationIngress.attemptCountForTest(context, "fcm:stopped-id") shouldBe 0
    }

    test("initialization exception is retried instead of escaping the worker") {
        coEvery { OneSignal.initWithContext(any()) } throws IllegalStateException("init failed")

        val result = NotificationIngressDrainWorker(context, mockk(relaxed = true)).doWork()

        result.javaClass shouldBe ListenableWorker.Result.retry().javaClass
    }

    test("initialization failure stops retrying after the bounded limit") {
        coEvery { OneSignal.initWithContext(any()) } returns false
        val workerParameters = mockk<WorkerParameters>(relaxed = true)
        every { workerParameters.runAttemptCount } returns NotificationIngressDrainWorker.MAX_INIT_ATTEMPTS - 1

        val result = NotificationIngressDrainWorker(context, workerParameters).doWork()

        result.javaClass shouldBe ListenableWorker.Result.failure().javaClass
    }
})
