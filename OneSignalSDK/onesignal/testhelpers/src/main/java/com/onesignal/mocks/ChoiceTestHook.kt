package com.onesignal.mocks

import com.onesignal.IOneSignal
import com.onesignal.common.services.IServiceProvider
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Installs a stand-in for the process-wide SDK implementation.
 *
 * `OneSignal.getService` is inline and the object mock does not reliably intercept the suspend
 * `initWithContext`, so both can reach the real implementation and run a real init in a unit test.
 */
object ChoiceTestHook {
    // Reflective because OneSignalChoice is internal to :core and this helper is a separate module.
    private val choice by lazy { Class.forName("com.onesignal.internal.OneSignalChoice") }
    private val instance by lazy { choice.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null) }
    private val chosen by lazy { choice.getDeclaredField("chosen").apply { isAccessible = true } }

    /** What the stand-in returns from `initWithContext`, for specs that drive init success. */
    var initResult: () -> Boolean = { false }

    /** A relaxed stand-in that also answers [IServiceProvider], so `getService` resolves from it. */
    fun install(): IOneSignal {
        initResult = { false }
        val fake = mockk<IOneSignal>(relaxed = true, moreInterfaces = arrayOf(IServiceProvider::class))
        coEvery { fake.initWithContext(any()) } answers { initResult() }
        publish(fake)
        return fake
    }

    fun publish(impl: IOneSignal?) = chosen.set(instance, impl)

    fun clear() {
        initResult = { false }
        publish(null)
    }
}
