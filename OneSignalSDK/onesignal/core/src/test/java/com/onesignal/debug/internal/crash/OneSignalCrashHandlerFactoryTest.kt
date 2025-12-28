package com.onesignal.debug.internal.crash

import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.time.ITime
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.user.internal.identity.IdentityModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.robolectric.annotation.Config

class OneSignalCrashHandlerFactoryTest : FunSpec({
    val mockApplicationService = mockk<IApplicationService>(relaxed = true)
    val mockInstallIdService = mockk<IInstallIdService>(relaxed = true)
    val mockConfigModelStore = mockk<ConfigModelStore>(relaxed = true)
    val mockIdentityModelStore = mockk<IdentityModelStore>(relaxed = true)
    val mockTime = mockk<ITime>(relaxed = true)

    beforeEach {
        every { mockTime.processUptimeMillis } returns 100000L
    }

    test("createCrashHandler should return IOtelCrashHandler") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    @Config(sdk = [Build.VERSION_CODES.O])
    test("createCrashHandler should create Otel handler for SDK 26+") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        handler shouldNotBe null
        // Should be able to initialize
        handler.initialize()
    }

    @Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
    test("createCrashHandler should return no-op handler for SDK < 26") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        handler shouldNotBe null
        handler.initialize() // Should not crash
    }
})
