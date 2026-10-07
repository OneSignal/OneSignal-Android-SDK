package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import io.mockk.mockk

/**
 * The framework only hands a receiver its PendingResult during a real dispatch, and the ordered
 * flag is a field rather than a method, so neither can be stubbed. Reflection stands in for the
 * dispatch so goAsync() returns this mock and the receiver's finish() path can be verified.
 */
internal fun BroadcastReceiver.attachPendingResult(ordered: Boolean = false): BroadcastReceiver.PendingResult {
    val pendingResult = mockk<BroadcastReceiver.PendingResult>(relaxed = true)
    BroadcastReceiver.PendingResult::class.java.getDeclaredField("mOrderedHint").apply {
        isAccessible = true
        setBoolean(pendingResult, ordered)
    }
    BroadcastReceiver::class.java.getDeclaredField("mPendingResult").apply {
        isAccessible = true
        set(this@attachPendingResult, pendingResult)
    }
    return pendingResult
}
