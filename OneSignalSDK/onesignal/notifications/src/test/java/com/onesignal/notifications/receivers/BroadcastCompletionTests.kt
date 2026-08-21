package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

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
        var finishThread = ""
        every { pendingResult.finish() } answers {
            finishThread = Thread.currentThread().name
        }
        BroadcastCompletion("test", pendingResult, 50)

        verify(exactly = 1, timeout = 1_000) { pendingResult.finish() }
        finishThread shouldBe "OS_BroadcastDeadline"
    }
})
