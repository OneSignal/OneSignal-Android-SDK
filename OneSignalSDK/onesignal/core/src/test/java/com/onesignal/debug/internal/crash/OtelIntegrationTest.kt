package com.onesignal.debug.internal.crash

import android.os.Build
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelPlatformProvider
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelPlatformProvider
import com.onesignal.otel.OtelFactory
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.robolectric.annotation.Config
import java.util.UUID
import android.content.Context as AndroidContext

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OtelIntegrationTest : FunSpec({
    val mockContext = mockk<AndroidContext>(relaxed = true)
    val mockApplicationService = mockk<IApplicationService>(relaxed = true)
    val mockInstallIdService = mockk<IInstallIdService>(relaxed = true)
    val mockConfigModelStore = mockk<ConfigModelStore>(relaxed = true)
    val mockIdentityModelStore = mockk<IdentityModelStore>(relaxed = true)
    val mockTime = mockk<ITime>(relaxed = true)
    val mockConfigModel = mockk<ConfigModel>(relaxed = true)
    val mockIdentityModel = mockk<IdentityModel>(relaxed = true)

    beforeEach {
        every { mockContext.packageName } returns "com.test.app"
        every { mockContext.cacheDir } returns mockk(relaxed = true) {
            every { path } returns "/test/cache"
        }
        every { mockApplicationService.appContext } returns mockContext
        every { mockApplicationService.isInForeground } returns true
        coEvery { mockInstallIdService.getId() } returns UUID.randomUUID()
        every { mockConfigModelStore.model } returns mockConfigModel
        every { mockIdentityModelStore.model } returns mockIdentityModel
        every { mockTime.processUptimeMillis } returns 100000L
        every { mockConfigModel.appId } returns "test-app-id"
        every { mockConfigModel.remoteLoggingParams } returns mockk(relaxed = true) {
            every { enable } returns true
        }
        every { mockIdentityModel.onesignalId } returns "test-onesignal-id"
        every { mockConfigModel.pushSubscriptionId } returns "test-subscription-id"
    }

    test("AndroidOtelPlatformProvider should provide correct Android values") {
        val provider = AndroidOtelPlatformProvider(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        provider.shouldBeInstanceOf<IOtelPlatformProvider>()
        provider.sdkBase shouldBe "android"
        provider.appPackageId shouldBe "com.test.app"
        provider.osName shouldBe "Android"
        provider.deviceManufacturer shouldBe Build.MANUFACTURER
        provider.deviceModel shouldBe Build.MODEL
        provider.osVersion shouldBe Build.VERSION.RELEASE
        provider.osBuildId shouldBe Build.ID

        runBlocking {
            provider.getInstallId() shouldNotBe null
        }
    }

    test("AndroidOtelPlatformProvider should provide per-event values") {
        val provider = AndroidOtelPlatformProvider(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        provider.appId shouldBe "test-app-id"
        provider.onesignalId shouldBe "test-onesignal-id"
        provider.pushSubscriptionId shouldBe "test-subscription-id"
        provider.appState shouldBe "foreground"
        provider.processUptime shouldBe 100.0
        provider.currentThreadName shouldBe Thread.currentThread().name
    }

    test("AndroidOtelLogger should delegate to Logging") {
        val logger = AndroidOtelLogger()

        logger.shouldBeInstanceOf<com.onesignal.otel.IOtelLogger>()
        // Should not throw
        logger.debug("test")
        logger.info("test")
        logger.warn("test")
        logger.error("test")
    }

    test("OtelFactory should create crash handler with Android provider") {
        val provider = AndroidOtelPlatformProvider(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )
        val logger = AndroidOtelLogger()

        val handler = OtelFactory.createCrashHandler(provider, logger)

        handler shouldNotBe null
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
        handler.initialize() // Should not throw
    }

    test("OneSignalCrashHandlerFactory should create working crash handler") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        handler shouldNotBe null
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
        handler.initialize() // Should not throw
    }

    test("AndroidOtelPlatformProvider should provide crash storage path") {
        val provider = AndroidOtelPlatformProvider(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        provider.crashStoragePath.contains("onesignal") shouldBe true
        provider.crashStoragePath.contains("otel") shouldBe true
        provider.crashStoragePath.contains("crashes") shouldBe true
        provider.minFileAgeForReadMillis shouldBe 5000L
    }

    test("AndroidOtelPlatformProvider should handle remote logging config") {
        every { mockConfigModel.remoteLoggingParams } returns mockk(relaxed = true) {
            every { enable } returns true
        }

        val provider = AndroidOtelPlatformProvider(
            mockApplicationService,
            mockInstallIdService,
            mockConfigModelStore,
            mockIdentityModelStore,
            mockTime
        )

        provider.remoteLoggingEnabled shouldBe true
        provider.appIdForHeaders shouldBe "test-app-id"
    }
})
