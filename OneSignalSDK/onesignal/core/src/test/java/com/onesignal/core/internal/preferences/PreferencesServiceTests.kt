package com.onesignal.core.internal.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.onesignal.core.internal.preferences.impl.PreferencesService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.extensions.RobolectricTest
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import kotlinx.coroutines.delay
import org.junit.runner.RunWith

@RobolectricTest
@RunWith(KotestTestRunner::class)
class PreferencesServiceTests : FunSpec({
    val mockPrefStoreName = PreferenceStores.ONESIGNAL
    val mockBoolPrefStoreKey = "mock-bool"
    val mockIntPrefStoreKey = "mock-int"
    val mockLongPrefStoreKey = "mock-long"
    val mockStringPrefStoreKey = "mock-string"
    val mockStringSetPrefStoreKey = "mock-string-set"

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("throws exception when store is not known") {
        // Given
        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        shouldThrowUnit<Exception> {
            preferencesService.getBool("not-known-store", mockBoolPrefStoreKey)
        }

        shouldThrowUnit<Exception> {
            preferencesService.saveBool("not-known-store", mockBoolPrefStoreKey, false)
        }

        // Then
    }

    test("retrieve preference with no default returns internal default when not in android shared preferences") {
        // Given
        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        val boolValue = preferencesService.getBool(mockPrefStoreName, mockBoolPrefStoreKey)
        val intValue = preferencesService.getInt(mockPrefStoreName, mockIntPrefStoreKey)
        val longValue = preferencesService.getLong(mockPrefStoreName, mockLongPrefStoreKey)
        val stringValue = preferencesService.getString(mockPrefStoreName, mockStringPrefStoreKey)
        val stringSetValue = preferencesService.getStringSet(mockPrefStoreName, mockStringSetPrefStoreKey)

        // Then
        boolValue shouldBe false
        intValue shouldBe 0
        longValue shouldBe 0
        stringValue shouldBe null
        stringSetValue shouldBe null
    }

    test("retrieve preference with default returns specified default when not in android shared preferences") {
        // Given
        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        val boolValue = preferencesService.getBool(mockPrefStoreName, mockBoolPrefStoreKey, true)
        val intValue = preferencesService.getInt(mockPrefStoreName, mockIntPrefStoreKey, 10)
        val longValue = preferencesService.getLong(mockPrefStoreName, mockLongPrefStoreKey, 20)
        val stringValue = preferencesService.getString(mockPrefStoreName, mockStringPrefStoreKey, "default")
        val stringSetValue = preferencesService.getStringSet(mockPrefStoreName, mockStringSetPrefStoreKey, setOf("default1", "default2"))

        // Then
        boolValue shouldBe true
        intValue shouldBe 10
        longValue shouldBe 20
        stringValue shouldBe "default"
        stringSetValue shouldBe setOf("default1", "default2")
    }

    test("retrieve preference returns what's in android shared preferences") {
        // Given
        val store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(mockPrefStoreName, Context.MODE_PRIVATE)
        val editor = store.edit()
        editor.putBoolean(mockBoolPrefStoreKey, true)
        editor.putInt(mockIntPrefStoreKey, 10)
        editor.putLong(mockLongPrefStoreKey, 20)
        editor.putString(mockStringPrefStoreKey, "default")
        editor.putStringSet(mockStringSetPrefStoreKey, setOf("default1", "default2"))
        editor.apply()

        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        val boolValue = preferencesService.getBool(mockPrefStoreName, mockBoolPrefStoreKey)
        val intValue = preferencesService.getInt(mockPrefStoreName, mockIntPrefStoreKey)
        val longValue = preferencesService.getLong(mockPrefStoreName, mockLongPrefStoreKey)
        val stringValue = preferencesService.getString(mockPrefStoreName, mockStringPrefStoreKey)
        val stringSetValue = preferencesService.getStringSet(mockPrefStoreName, mockStringSetPrefStoreKey)

        // Then
        boolValue shouldBe true
        intValue shouldBe 10
        longValue shouldBe 20
        stringValue shouldBe "default"
        stringSetValue shouldBe setOf("default1", "default2")
    }

    test("retrieve preference returns default value when in android shared preferences but is different type") {
        // Given
        val store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(mockPrefStoreName, Context.MODE_PRIVATE)
        val editor = store.edit()
        editor.putInt(mockBoolPrefStoreKey, 0)
        editor.putBoolean(mockIntPrefStoreKey, true)
        editor.putBoolean(mockLongPrefStoreKey, true)
        editor.putBoolean(mockStringPrefStoreKey, true)
        editor.putBoolean(mockStringSetPrefStoreKey, true)
        editor.apply()

        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        val boolValue = preferencesService.getBool(mockPrefStoreName, mockBoolPrefStoreKey)
        val intValue = preferencesService.getInt(mockPrefStoreName, mockIntPrefStoreKey)
        val longValue = preferencesService.getLong(mockPrefStoreName, mockLongPrefStoreKey)
        val stringValue = preferencesService.getString(mockPrefStoreName, mockStringPrefStoreKey)
        val stringSetValue = preferencesService.getStringSet(mockPrefStoreName, mockStringSetPrefStoreKey)

        // Then
        boolValue shouldBe false
        intValue shouldBe 0
        longValue shouldBe 0
        stringValue shouldBe null
        stringSetValue shouldBe null
    }

    test("retrieve preference returns what's in cache rather than android shared preferences") {
        // Given
        val store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(mockPrefStoreName, Context.MODE_PRIVATE)
        val editor = store.edit()
        editor.putBoolean(mockBoolPrefStoreKey, false)
        editor.putInt(mockIntPrefStoreKey, 100)
        editor.putLong(mockLongPrefStoreKey, 200)
        editor.putString(mockStringPrefStoreKey, "default1")
        editor.putStringSet(mockStringSetPrefStoreKey, setOf("default1-1", "default1-2"))
        editor.apply()

        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        preferencesService.saveBool(mockPrefStoreName, mockBoolPrefStoreKey, true)
        preferencesService.saveInt(mockPrefStoreName, mockIntPrefStoreKey, 10)
        preferencesService.saveLong(mockPrefStoreName, mockLongPrefStoreKey, 20)
        preferencesService.saveString(mockPrefStoreName, mockStringPrefStoreKey, "default2")
        preferencesService.saveStringSet(mockPrefStoreName, mockStringSetPrefStoreKey, setOf("default2-1", "default2-2"))

        val boolValue = preferencesService.getBool(mockPrefStoreName, mockBoolPrefStoreKey)
        val intValue = preferencesService.getInt(mockPrefStoreName, mockIntPrefStoreKey)
        val longValue = preferencesService.getLong(mockPrefStoreName, mockLongPrefStoreKey)
        val stringValue = preferencesService.getString(mockPrefStoreName, mockStringPrefStoreKey)
        val stringSetValue = preferencesService.getStringSet(mockPrefStoreName, mockStringSetPrefStoreKey)

        // Then
        boolValue shouldBe true
        intValue shouldBe 10
        longValue shouldBe 20
        stringValue shouldBe "default2"
        stringSetValue shouldBe setOf("default2-1", "default2-2")
    }

    test("save preference are stored in android shared preferences") {
        // Given
        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        preferencesService.saveBool(mockPrefStoreName, mockBoolPrefStoreKey, true)
        preferencesService.saveInt(mockPrefStoreName, mockIntPrefStoreKey, 10)
        preferencesService.saveLong(mockPrefStoreName, mockLongPrefStoreKey, 20)
        preferencesService.saveString(mockPrefStoreName, mockStringPrefStoreKey, "default")
        preferencesService.saveStringSet(mockPrefStoreName, mockStringSetPrefStoreKey, setOf("default1", "default2"))
        preferencesService.start()

        delay(1000)

        val store =
            ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(
                PreferenceStores.ONESIGNAL,
                Context.MODE_PRIVATE,
            )

        // Then
        store.getBoolean(mockBoolPrefStoreKey, false) shouldBe true
        store.getInt(mockIntPrefStoreKey, 0) shouldBe 10
        store.getLong(mockLongPrefStoreKey, 0) shouldBe 20
        store.getString(mockStringPrefStoreKey, null) shouldBe "default"
        store.getStringSet(mockStringSetPrefStoreKey, null) shouldBe setOf("default1", "default2")
    }

    test("save preference as null will remove from android shared preferences store") {
        // Given
        val store = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(mockPrefStoreName, Context.MODE_PRIVATE)
        val editor = store.edit()
        editor.putBoolean(mockBoolPrefStoreKey, false)
        editor.putInt(mockIntPrefStoreKey, 100)
        editor.putLong(mockLongPrefStoreKey, 200)
        editor.putString(mockStringPrefStoreKey, "default1")
        editor.putStringSet(mockStringSetPrefStoreKey, setOf("default1-1", "default1-2"))
        editor.apply()

        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        // When
        preferencesService.saveBool(mockPrefStoreName, mockBoolPrefStoreKey, null)
        preferencesService.saveInt(mockPrefStoreName, mockIntPrefStoreKey, null)
        preferencesService.saveLong(mockPrefStoreName, mockLongPrefStoreKey, null)
        preferencesService.saveString(mockPrefStoreName, mockStringPrefStoreKey, null)
        preferencesService.saveStringSet(mockPrefStoreName, mockStringSetPrefStoreKey, null)
        preferencesService.start()

        delay(1000)

        // Then
        store.contains(mockBoolPrefStoreKey) shouldBe false
        store.contains(mockIntPrefStoreKey) shouldBe false
        store.contains(mockLongPrefStoreKey) shouldBe false
        store.contains(mockStringPrefStoreKey) shouldBe false
        store.contains(mockStringSetPrefStoreKey) shouldBe false
    }
})
