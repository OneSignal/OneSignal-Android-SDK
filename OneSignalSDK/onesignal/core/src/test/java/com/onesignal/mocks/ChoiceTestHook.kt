package com.onesignal.mocks

import com.onesignal.IOneSignal
import com.onesignal.internal.OneSignalChoice

/**
 * Publishes a stand-in implementation into [OneSignalChoice].
 *
 * `OneSignal.getService` is inline and the object mock does not reliably intercept the suspend
 * `initWithContext`, so both can reach the real implementation and run a real init in a unit test.
 */
internal object ChoiceTestHook {
    private val chosen =
        OneSignalChoice::class.java
            .getDeclaredField("chosen")
            .apply { isAccessible = true }

    fun publish(impl: IOneSignal?) = chosen.set(OneSignalChoice, impl)
}
