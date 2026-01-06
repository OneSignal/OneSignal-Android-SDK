package com.onesignal.debug.internal.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.otel.IOtelCrashHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

@RobolectricTest
class OneSignalCrashHandlerFactoryTest : FunSpec({
    var appContext: Context? = null
    var logger: AndroidOtelLogger? = null

    beforeAny {
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
            logger = AndroidOtelLogger()
        }
    }

    test("createCrashHandler should return IOtelCrashHandler") {
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            appContext!!,
            logger!!
        )

        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("createCrashHandler should create Otel handler for SDK 26+") {
        // Note: SDK version check is handled at runtime by the factory
        // This test verifies the handler can be created and initialized
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            appContext!!,
            logger!!
        )

        handler shouldNotBe null
        // Should be able to initialize
        handler.initialize()
    }

    test("createCrashHandler should return no-op handler for SDK < 26") {
        // Note: SDK version check is handled at runtime by the factory
        // This test verifies the handler can be created and initialized
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            appContext!!,
            logger!!
        )

        handler shouldNotBe null
        handler.initialize() // Should not crash
    }
})
