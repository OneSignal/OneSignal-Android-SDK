package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RobolectricTest
class BroadcastCompletionTests : FunSpec({
    test("finish is exact once") {
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        val completion = BroadcastCompletion("test", pendingResult)

        completion.finish()
        completion.finish("deadline")

        verify(exactly = 1) { pendingResult.finish() }
    }

    test("reconstructible work finishes at its deadline") {
        val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
        val finishThread = AtomicReference<String?>(null)
        val finished = CountDownLatch(1)
        every { pendingResult.finish() } answers {
            finishThread.set(Thread.currentThread().name)
            finished.countDown()
        }
        BroadcastCompletion("test", pendingResult, 50)

        // Wait on the answer, not on mockk: verify(timeout) returns once the call is recorded,
        // which can be before the answer above has run.
        finished.await(5, TimeUnit.SECONDS) shouldBe true
        finishThread.get() shouldBe "OS_BroadcastDeadline"
        verify(exactly = 1) { pendingResult.finish() }
    }
})
