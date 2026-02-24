package com.onesignal.debug.internal.crash

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OneSignalCrashHandlerFactoryTest : FunSpec({
    lateinit var appContext: Context
    lateinit var logger: AndroidOtelLogger
    val originalHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    beforeAny {
        appContext = ApplicationProvider.getApplicationContext()
        logger = AndroidOtelLogger()
    }

    afterEach {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        OtelSdkSupport.reset()
    }

    test("createCrashHandler should return IOtelCrashHandler") {
        OtelSdkSupport.isSupported = true
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)

        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("createCrashHandler should create handler that can be initialized") {
        OtelSdkSupport.isSupported = true
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)

        handler shouldNotBe null
        handler.initialize()
    }

    test("createCrashHandler should throw when SDK is unsupported") {
        OtelSdkSupport.isSupported = false
        shouldThrow<IllegalArgumentException> {
            OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)
        }
    }

    test("createCrashHandler should accept mock logger") {
        OtelSdkSupport.isSupported = true
        val mockLogger = mockk<IOtelLogger>(relaxed = true)

        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, mockLogger)

        handler shouldNotBe null
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("handler should be idempotent when initialized multiple times") {
        OtelSdkSupport.isSupported = true
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)

        handler.initialize()
        handler.initialize()

        handler shouldNotBe null
    }

    test("createCrashHandler should work with different contexts") {
        OtelSdkSupport.isSupported = true
        val context1: Context = ApplicationProvider.getApplicationContext()
        val context2: Context = ApplicationProvider.getApplicationContext()

        val handler1 = OneSignalCrashHandlerFactory.createCrashHandler(context1, logger)
        val handler2 = OneSignalCrashHandlerFactory.createCrashHandler(context2, logger)

        handler1 shouldNotBe null
        handler2 shouldNotBe null
    }
})
