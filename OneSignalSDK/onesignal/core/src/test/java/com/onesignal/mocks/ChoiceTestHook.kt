package com.onesignal.mocks

import com.onesignal.IOneSignal
import com.onesignal.internal.OneSignalChoice

/**
 * Installs a stand-in implementation in [OneSignalChoice].
 *
 * `OneSignal.getService` is inline and the object mock does not reliably intercept the suspend
 * `initWithContext`, so both can reach the real implementation and run a real init in a unit test.
 */
internal object ChoiceTestHook {
    fun publish(impl: IOneSignal?) {
        OneSignalChoice.chosen = impl
    }
}
