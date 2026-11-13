package com.onesignal.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.json.JSONArray

@RobolectricTest
class NeverCrashKillSwitchTests : FunSpec({
    val configKey = PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE

    fun prefs() = getApplicationContext<Context>().getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)

    // Written through the real model serializer, so a change to the stored format fails here.
    fun cacheFlags(vararg keys: String) {
        val config = ConfigModel().apply { sdkRemoteFeatureFlags = keys.toList() }
        prefs().edit().putString(configKey, JSONArray().put(config.toJSON()).toString()).commit()
    }

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        prefs().edit().clear().commit()
        NeverCrashKillSwitch.resetForTest()
    }

    afterEach { NeverCrashKillSwitch.resetForTest() }

    test("the cached flag selects the never-crash implementation") {
        cacheFlags("other_flag", NeverCrashKillSwitch.FLAG_KEY)

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe false
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("the cached flag matches regardless of wire casing") {
        cacheFlags(NeverCrashKillSwitch.FLAG_KEY.uppercase())

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe false
    }

    test("without the flag the legacy implementation is used") {
        cacheFlags("other_flag")

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe true
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImp>()
    }

    test("a fresh install with nothing cached uses the legacy implementation") {
        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe true
    }

    test("an unreadable cached config keeps the legacy implementation without throwing") {
        prefs().edit().putString(configKey, "{not json").commit()

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.evaluated shouldBe true
        NeverCrashKillSwitch.useLegacy shouldBe true
    }

    test("a locked device keeps the legacy implementation") {
        cacheFlags(NeverCrashKillSwitch.FLAG_KEY)
        mockkObject(AndroidUtils)
        try {
            every { AndroidUtils.isAndroidUserUnlocked(any()) } returns false

            NeverCrashKillSwitch.evaluate(getApplicationContext())

            NeverCrashKillSwitch.useLegacy shouldBe true
        } finally {
            unmockkObject(AndroidUtils)
        }
    }

    test("first use before the flag is evaluated uses the legacy implementation") {
        cacheFlags(NeverCrashKillSwitch.FLAG_KEY)

        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImp>()

        NeverCrashKillSwitch.evaluated shouldBe false
    }

    test("a later evaluate does not flip a decision already made") {
        cacheFlags(NeverCrashKillSwitch.FLAG_KEY)
        NeverCrashKillSwitch.evaluate(getApplicationContext())
        prefs().edit().clear().commit()

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe false
    }
})
