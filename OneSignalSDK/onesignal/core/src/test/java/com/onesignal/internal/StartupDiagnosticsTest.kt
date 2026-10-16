package com.onesignal.internal

import android.content.Context
import android.content.pm.PackageInfo
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.debug.internal.logging.logger.android.getCrashStoragePath
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
        oneSignal.logStartupDiagnostics(context)

        // The listener is global, so background work left by other specs can log here too.
        val diagnostics = events.single { it.entry.contains("observabilityModule=") }
        diagnostics.level shouldBe LogLevel.WARN
        diagnostics.entry shouldContain "observabilityModule=logger"
        diagnostics.entry shouldContain "app=com.example@1.2.3"
        diagnostics.entry shouldContain "crashDir=${getCrashStoragePath(context)}"
    }
})
