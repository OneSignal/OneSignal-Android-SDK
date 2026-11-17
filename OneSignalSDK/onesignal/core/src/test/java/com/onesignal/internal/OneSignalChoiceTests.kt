package com.onesignal.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.IOneSignal
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.core.internal.features.StartupFlagsCache
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.util.concurrent.CopyOnWriteArrayList

@RobolectricTest
class OneSignalChoiceTests : FunSpec({
    fun context() = getApplicationContext<Context>()

    fun cacheNeverCrashFlag() = StartupFlagsCache.write(context()) { listOf(NeverCrashKillSwitch.FLAG_KEY) }

    fun resetChoice() {
        OneSignalChoice.chosen = null
        listOf("pendingConsentRequired", "pendingConsentGiven", "pendingDisableGmsMissingPrompt")
            .forEach { OneSignalChoice::class.java.getDeclaredField(it).apply { isAccessible = true }.set(OneSignalChoice, null) }
    }

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        FeatureManager.resetStartupFlagsForTest()
        NeverCrashKillSwitch.resetForTest()
        resetChoice()
    }

    afterEach {
        Logging.logLevel = LogLevel.NONE
        Logging.visualLogLevel = LogLevel.NONE
        FeatureManager.resetStartupFlagsForTest()
        NeverCrashKillSwitch.resetForTest()
        resetChoice()
    }

    test("settings touched before init leave the implementation unchosen, so the flag still decides") {
        cacheNeverCrashFlag()

        OneSignalChoice.debug
        OneSignalChoice.isInitialized shouldBe false
        OneSignalChoice.consentRequired shouldBe false
        OneSignalChoice.consentRequired = true
        OneSignalChoice.consentGiven = true
        OneSignalChoice.disableGMSMissingPrompt = true

        OneSignalChoice.resolve(context()).shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("suspend consent accessors before init leave the implementation unchosen") {
        cacheNeverCrashFlag()

        OneSignalChoice.setConsentRequired(true)
        OneSignalChoice.setConsentGiven(true)
        OneSignalChoice.setDisableGMSMissingPrompt(true)
        OneSignalChoice.getConsentRequired() shouldBe true
        OneSignalChoice.getConsentGiven() shouldBe true
        OneSignalChoice.getDisableGMSMissingPrompt() shouldBe true

        OneSignalChoice.resolve(context()).shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("consent stashed before the implementation exists is applied to it") {
        cacheNeverCrashFlag()
        OneSignalChoice.consentRequired = true
        OneSignalChoice.consentGiven = true
        OneSignalChoice.disableGMSMissingPrompt = true

        val impl = OneSignalChoice.resolve(context())

        impl.consentRequired shouldBe true
        impl.consentGiven shouldBe true
        impl.disableGMSMissingPrompt shouldBe true
    }

    test("a setting written after the implementation exists reaches it instead of the stash") {
        cacheNeverCrashFlag()
        val impl = OneSignalChoice.resolve(context())

        OneSignalChoice.consentGiven = true

        impl.consentGiven shouldBe true
        OneSignalChoice.consentGiven shouldBe true
    }

    test("log levels set before the choice survive building the implementation") {
        cacheNeverCrashFlag()
        Logging.logLevel = LogLevel.VERBOSE
        Logging.visualLogLevel = LogLevel.ERROR

        OneSignalChoice.resolve(context())

        Logging.logLevel shouldBe LogLevel.VERBOSE
        Logging.visualLogLevel shouldBe LogLevel.ERROR
    }

    test("the first implementation is kept for the rest of the process") {
        cacheNeverCrashFlag()

        val first = OneSignalChoice.resolve(context())

        OneSignalChoice.resolve() shouldBeSameInstanceAs first
        OneSignalChoice.resolve(context()) shouldBeSameInstanceAs first
    }

    test("resolving without a context before the flag is read keeps legacy for the process") {
        cacheNeverCrashFlag()

        val first = OneSignalChoice.resolve()

        first.shouldBeInstanceOf<OneSignalImp>()
        OneSignalChoice.resolve(context()) shouldBeSameInstanceAs first
    }

    test("a log listener that re-enters during the choice gets the published implementation") {
        cacheNeverCrashFlag()
        val seen = CopyOnWriteArrayList<IOneSignal>()
        val listener = ILogListener { seen += OneSignalChoice.resolve() }
        Logging.addListener(listener)
        val impl =
            try {
                OneSignalChoice.resolve(context())
            } finally {
                Logging.removeListener(listener)
            }

        // One implementation for the process: anything registered on a second one would be lost.
        seen.size shouldBe 1
        seen.forEach { it shouldBeSameInstanceAs impl }
        OneSignalChoice.resolve() shouldBeSameInstanceAs impl
        impl.shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("without the cached flag a context resolves to the legacy implementation") {
        OneSignalChoice.resolve(context()).shouldBeInstanceOf<OneSignalImp>()
    }
})
