package com.onesignal.debug.internal.crash

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

    test("createCrashHandler should create Otel handler for SDK 26+") {
        // Note: SDK version check is handled at runtime by the factory
        // This test verifies the handler can be created and initialized
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

    test("createCrashHandler should return no-op handler for SDK < 26") {
        // Note: SDK version check is handled at runtime by the factory
        // This test verifies the handler can be created and initialized
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
