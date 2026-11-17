package com.onesignal.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.core.internal.features.PrefsStartupFlagsStore
import com.onesignal.core.internal.features.StartupFlagsCache
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

@RobolectricTest
class NeverCrashKillSwitchTests : FunSpec({
    fun context() = getApplicationContext<Context>()

    // Written through the production mirror, so a change to the stored format fails here.
    fun mirrorFlags(vararg keys: String) = StartupFlagsCache.write(context()) { keys.toList() }

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        FeatureManager.resetStartupFlagsForTest()
        NeverCrashKillSwitch.resetForTest()
    }

    afterEach {
        FeatureManager.resetStartupFlagsForTest()
        NeverCrashKillSwitch.resetForTest()
    }

    test("the mirrored flag selects the never-crash implementation") {
        mirrorFlags("other_flag", NeverCrashKillSwitch.FLAG_KEY)

        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.useLegacy shouldBe false
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("the mirrored flag matches regardless of wire casing") {
        mirrorFlags(NeverCrashKillSwitch.FLAG_KEY.uppercase())

        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.useLegacy shouldBe false
    }

    test("without the flag the legacy implementation is used") {
        mirrorFlags("other_flag")

        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.useLegacy shouldBe true
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImp>()
    }

    test("a fresh install with nothing mirrored uses the legacy implementation") {
        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.useLegacy shouldBe true
    }

    test("an unreadable store keeps the legacy implementation without throwing") {
        mirrorFlags(NeverCrashKillSwitch.FLAG_KEY)
        FeatureManager.resetStartupFlagsForTest()
        PrefsStartupFlagsStore.commitFailed = true

        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.evaluated shouldBe true
        NeverCrashKillSwitch.useLegacy shouldBe true
    }

    // The store is device protected, so unlike the old config-model read this works before first unlock.
    test("a locked device still reads the mirrored flag") {
        mirrorFlags(NeverCrashKillSwitch.FLAG_KEY)

        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.useLegacy shouldBe false
    }

    test("first use before the flag is evaluated uses the legacy implementation") {
        mirrorFlags(NeverCrashKillSwitch.FLAG_KEY)

        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImp>()

        NeverCrashKillSwitch.evaluated shouldBe false
    }

    test("a later evaluate does not flip a decision already made") {
        mirrorFlags(NeverCrashKillSwitch.FLAG_KEY)
        NeverCrashKillSwitch.evaluate(context())
        FeatureManager.resetStartupFlagsForTest()
        mirrorFlags()

        NeverCrashKillSwitch.evaluate(context())

        NeverCrashKillSwitch.useLegacy shouldBe false
    }
})
