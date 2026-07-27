package com.onesignal.internal

import android.content.Context
import android.content.pm.PackageInfo
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.otel.android.getOtelCrashStoragePath
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import java.io.File

class StartupDiagnosticsTest : FunSpec({
    val events = mutableListOf<OneSignalLogEvent>()
    val listener = ILogListener { events.add(it) }

    beforeEach {
        events.clear()
        Logging.logLevel = LogLevel.NONE
        Logging.addListener(listener)
    }

    afterEach {
        Logging.removeListener(listener)
    }

    test("startup diagnostics are visible and use the shared crash path") {
        val packageInfo = PackageInfo().apply { versionName = "1.2.3" }
        val context = mockk<Context>()
        every { context.packageName } returns "com.example"
        every { context.cacheDir } returns File("/tmp/cache")
        every { context.packageManager.getPackageInfo("com.example", 0) } returns packageInfo

        val oneSignal = OneSignalImp()
        Logging.logLevel = LogLevel.NONE
        oneSignal.logStartupDiagnostics(context, useLoggerModule = false)

        events.size shouldBe 1
        events.single().level shouldBe LogLevel.WARN
        events.single().entry shouldContain "observabilityModule=otel"
        events.single().entry shouldContain "SDK_CUSTOM_LOGGING=false"
        events.single().entry shouldContain "app=com.example@1.2.3"
        events.single().entry shouldContain "crashDir=${getOtelCrashStoragePath(context)}"
    }
})
