package com.onesignal.debug.internal.crash

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.otel.IOtelCrashHandler
import com.onesignal.otel.IOtelLogger
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

    beforeAny {
        appContext = ApplicationProvider.getApplicationContext()
        logger = AndroidOtelLogger()
    }

    afterEach {
        // Reset uncaught exception handler after each test
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    test("createCrashHandler should return IOtelCrashHandler") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)

        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("createCrashHandler should create handler that can be initialized") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)

        handler shouldNotBe null
        handler.initialize()
    }

    test("createCrashHandler should accept mock logger") {
        val mockLogger = mockk<IOtelLogger>(relaxed = true)

        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, mockLogger)

        handler shouldNotBe null
        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("handler should be idempotent when initialized multiple times") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(appContext, logger)

        handler.initialize()
        handler.initialize() // Should not throw

        handler shouldNotBe null
    }

    test("createCrashHandler should work with different contexts") {
        val context1: Context = ApplicationProvider.getApplicationContext()
        val context2: Context = ApplicationProvider.getApplicationContext()

        val handler1 = OneSignalCrashHandlerFactory.createCrashHandler(context1, logger)
        val handler2 = OneSignalCrashHandlerFactory.createCrashHandler(context2, logger)

        handler1 shouldNotBe null
        handler2 shouldNotBe null
    }
})
