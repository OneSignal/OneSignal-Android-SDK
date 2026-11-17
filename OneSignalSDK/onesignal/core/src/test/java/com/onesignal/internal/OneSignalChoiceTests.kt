package com.onesignal.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.IOneSignal
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.json.JSONArray
import java.util.concurrent.CopyOnWriteArrayList

@RobolectricTest
class OneSignalChoiceTests : FunSpec({
    val configKey = PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE

    fun prefs() = getApplicationContext<Context>().getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)

    fun cacheNeverCrashFlag() {
        val config = ConfigModel().apply { sdkRemoteFeatureFlags = listOf(NeverCrashKillSwitch.FLAG_KEY) }
        prefs().edit().putString(configKey, JSONArray().put(config.toJSON()).toString()).commit()
    }

    fun resetChoice() {
        OneSignalChoice.chosen = null
        listOf("pendingConsentRequired", "pendingConsentGiven", "pendingDisableGmsMissingPrompt")
            .forEach { OneSignalChoice::class.java.getDeclaredField(it).apply { isAccessible = true }.set(OneSignalChoice, null) }
    }

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        prefs().edit().clear().commit()
        NeverCrashKillSwitch.resetForTest()
        resetChoice()
    }

    afterEach {
        Logging.logLevel = LogLevel.NONE
        Logging.visualLogLevel = LogLevel.NONE
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

        OneSignalChoice.resolve(getApplicationContext()).shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("suspend consent accessors before init leave the implementation unchosen") {
        cacheNeverCrashFlag()

        OneSignalChoice.setConsentRequired(true)
        OneSignalChoice.setConsentGiven(true)
        OneSignalChoice.setDisableGMSMissingPrompt(true)
        OneSignalChoice.getConsentRequired() shouldBe true
        OneSignalChoice.getConsentGiven() shouldBe true
        OneSignalChoice.getDisableGMSMissingPrompt() shouldBe true

        OneSignalChoice.resolve(getApplicationContext()).shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("consent stashed before the implementation exists is applied to it") {
        cacheNeverCrashFlag()
        OneSignalChoice.consentRequired = true
        OneSignalChoice.consentGiven = true
        OneSignalChoice.disableGMSMissingPrompt = true

        val impl = OneSignalChoice.resolve(getApplicationContext())

        impl.consentRequired shouldBe true
        impl.consentGiven shouldBe true
        impl.disableGMSMissingPrompt shouldBe true
    }

    test("a setting written after the implementation exists reaches it instead of the stash") {
        cacheNeverCrashFlag()
        val impl = OneSignalChoice.resolve(getApplicationContext())

        OneSignalChoice.consentGiven = true

        impl.consentGiven shouldBe true
        OneSignalChoice.consentGiven shouldBe true
    }

    test("log levels set before the choice survive building the implementation") {
        cacheNeverCrashFlag()
        Logging.logLevel = LogLevel.VERBOSE
        Logging.visualLogLevel = LogLevel.ERROR

        OneSignalChoice.resolve(getApplicationContext())

        Logging.logLevel shouldBe LogLevel.VERBOSE
        Logging.visualLogLevel shouldBe LogLevel.ERROR
    }

    test("the first implementation is kept for the rest of the process") {
        cacheNeverCrashFlag()

        val first = OneSignalChoice.resolve(getApplicationContext())

        OneSignalChoice.resolve() shouldBeSameInstanceAs first
        OneSignalChoice.resolve(getApplicationContext()) shouldBeSameInstanceAs first
    }

    test("resolving without a context before the flag is read keeps legacy for the process") {
        cacheNeverCrashFlag()

        val first = OneSignalChoice.resolve()

        first.shouldBeInstanceOf<OneSignalImp>()
        OneSignalChoice.resolve(getApplicationContext()) shouldBeSameInstanceAs first
    }

    test("a log listener that re-enters during the choice gets the published implementation") {
        cacheNeverCrashFlag()
        val seen = CopyOnWriteArrayList<IOneSignal>()
        val listener = ILogListener { seen += OneSignalChoice.resolve() }
        Logging.addListener(listener)
        val impl =
            try {
                OneSignalChoice.resolve(getApplicationContext())
            } finally {
                Logging.removeListener(listener)
            }

        // One implementation for the process: anything registered on a second one would be lost.
        seen.size shouldBe 1
        seen.forEach { it shouldBeSameInstanceAs impl }
        OneSignalChoice.resolve() shouldBeSameInstanceAs impl
        impl.shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("a listener that re-enters while the choice is still being made keeps the first implementation") {
        // Unreadable cache: evaluate() warns while choiceLock is held and nothing is published yet,
        // which is the only path that reaches the re-check guarding against a second implementation.
        prefs().edit().putString(configKey, "{not json").commit()
        val seen = CopyOnWriteArrayList<IOneSignal>()
        val listener = ILogListener { seen += OneSignalChoice.resolve() }
        Logging.addListener(listener)
        val impl =
            try {
                OneSignalChoice.resolve(getApplicationContext())
            } finally {
                Logging.removeListener(listener)
            }

        seen.isEmpty() shouldBe false
        seen.forEach { it shouldBeSameInstanceAs impl }
        OneSignalChoice.resolve() shouldBeSameInstanceAs impl
    }

    test("without the cached flag a context resolves to the legacy implementation") {
        OneSignalChoice.resolve(getApplicationContext()).shouldBeInstanceOf<OneSignalImp>()
    }
})
