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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.concurrent.CountDownLatch
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
                NotificationIngress.persistFcm(context, Intent(), bundle)
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

    test("initialization failure stops retrying after the bounded limit") {
        coEvery { OneSignal.initWithContext(any()) } returns false
        val workerParameters = mockk<WorkerParameters>(relaxed = true)
        every { workerParameters.runAttemptCount } returns NotificationIngressDrainWorker.MAX_INIT_ATTEMPTS - 1

        val result = NotificationIngressDrainWorker(context, workerParameters).doWork()

        result.javaClass shouldBe ListenableWorker.Result.failure().javaClass
    }
})
