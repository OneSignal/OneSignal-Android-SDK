package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import android.os.Looper
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import io.mockk.verify
import org.robolectric.Shadows.shadowOf
import java.time.Duration

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
        BroadcastCompletion("test", pendingResult, 100)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))

        verify(exactly = 1) { pendingResult.finish() }
    }
})
