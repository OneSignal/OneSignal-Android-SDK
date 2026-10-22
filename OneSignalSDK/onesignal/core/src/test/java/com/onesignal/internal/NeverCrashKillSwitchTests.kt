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

    test("the cached kill switch selects the legacy implementation") {
        cacheFlags("other_flag", NeverCrashKillSwitch.FLAG_KEY)

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe true
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImp>()
    }

    test("the cached kill switch matches regardless of wire casing") {
        cacheFlags(NeverCrashKillSwitch.FLAG_KEY.uppercase())

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe true
    }

    test("without the kill switch the never-crash implementation is used") {
        cacheFlags("other_flag")

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe false
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImpV2>()
    }

    test("a fresh install with nothing cached uses the never-crash implementation") {
        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.useLegacy shouldBe false
    }

    test("an unreadable cached config keeps the never-crash implementation without throwing") {
        prefs().edit().putString(configKey, "{not json").commit()

        NeverCrashKillSwitch.evaluate(getApplicationContext())

        NeverCrashKillSwitch.evaluated shouldBe true
        NeverCrashKillSwitch.useLegacy shouldBe false
    }

    test("a locked device keeps the never-crash implementation") {
        cacheFlags(NeverCrashKillSwitch.FLAG_KEY)
        mockkObject(AndroidUtils)
        try {
            every { AndroidUtils.isAndroidUserUnlocked(any()) } returns false

            NeverCrashKillSwitch.evaluate(getApplicationContext())

            NeverCrashKillSwitch.useLegacy shouldBe false
        } finally {
            unmockkObject(AndroidUtils)
        }
    }

    test("first use before the switch is evaluated uses the never-crash implementation") {
        NeverCrashKillSwitch.create().shouldBeInstanceOf<OneSignalImpV2>()
    }
})
