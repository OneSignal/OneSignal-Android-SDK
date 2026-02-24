package com.onesignal.debug.internal.crash

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.internal.logging.otel.android.AndroidOtelLogger
import com.onesignal.otel.IOtelCrashHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.robolectric.annotation.Config

@RobolectricTest
@Config(sdk = [Build.VERSION_CODES.O])
class OneSignalCrashHandlerFactoryTest : FunSpec({
    var appContext: Context? = null
    var logger: AndroidOtelLogger? = null

    beforeAny {
        if (appContext == null) {
            appContext = ApplicationProvider.getApplicationContext()
            logger = AndroidOtelLogger()
        }
    }

    afterEach {
        OtelSdkSupport.reset()
    }

    test("createCrashHandler should return IOtelCrashHandler when supported") {
        OtelSdkSupport.isSupported = true
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            appContext!!,
            logger!!,
        )

        handler.shouldBeInstanceOf<IOtelCrashHandler>()
    }

    test("createCrashHandler should create Otel handler for SDK 26+") {
        OtelSdkSupport.isSupported = true
        val handler = OneSignalCrashHandlerFactory.createCrashHandler(
            appContext!!,
            logger!!,
        )

        handler shouldNotBe null
        handler.initialize()
    }

    test("createCrashHandler should throw when SDK is unsupported") {
        OtelSdkSupport.isSupported = false
        shouldThrow<IllegalArgumentException> {
            OneSignalCrashHandlerFactory.createCrashHandler(
                appContext!!,
                logger!!,
            )
        }
    }
})
