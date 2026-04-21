package com.onesignal.user.internal.subscriptions

import com.onesignal.common.PIIHasher
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.mocks.MockPreferencesService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.json.JSONArray

class SubscriptionModelStoreTests : FunSpec({

    fun getPersistedJson(prefs: IPreferencesService): JSONArray {
        val raw = prefs.getString(
            PreferenceStores.ONESIGNAL,
            PreferenceOneSignalKeys.MODEL_STORE_PREFIX + "subscriptions",
            null,
        )
        return JSONArray(raw!!)
    }

    test("persist hashes email address in SharedPreferences") {
        val prefs = MockPreferencesService()
        val store = SubscriptionModelStore(prefs)

        val model = SubscriptionModel()
        model.id = "email1"
        model.type = SubscriptionType.EMAIL
        model.address = "user@example.com"
        store.add(model)

        val json = getPersistedJson(prefs)
        val persisted = json.getJSONObject(0)
        persisted.getString("address") shouldBe PIIHasher.hash("user@example.com")
    }

    test("persist hashes SMS address in SharedPreferences") {
        val prefs = MockPreferencesService()
        val store = SubscriptionModelStore(prefs)

        val model = SubscriptionModel()
        model.id = "sms1"
        model.type = SubscriptionType.SMS
        model.address = "+15558675309"
        store.add(model)

        val json = getPersistedJson(prefs)
        val persisted = json.getJSONObject(0)
        persisted.getString("address") shouldBe PIIHasher.hash("+15558675309")
    }

    test("persist does not hash push token in SharedPreferences") {
        val prefs = MockPreferencesService()
        val store = SubscriptionModelStore(prefs)

        val pushToken = "dz1A0qydQGCYM9dDgo6rB_:APA91bEqFakeToken"
        val model = SubscriptionModel()
        model.id = "push1"
        model.type = SubscriptionType.PUSH
        model.address = pushToken
        store.add(model)

        val json = getPersistedJson(prefs)
        val persisted = json.getJSONObject(0)
        persisted.getString("address") shouldBe pushToken
    }

    test("persist does not double-hash already-hashed email") {
        val prefs = MockPreferencesService()
        val store = SubscriptionModelStore(prefs)

        val alreadyHashed = PIIHasher.hash("user@example.com")
        val model = SubscriptionModel()
        model.id = "email1"
        model.type = SubscriptionType.EMAIL
        model.address = alreadyHashed
        store.add(model)

        val json = getPersistedJson(prefs)
        val persisted = json.getJSONObject(0)
        persisted.getString("address") shouldBe alreadyHashed
    }

    test("persist keeps in-memory model address as raw value") {
        val prefs = MockPreferencesService()
        val store = SubscriptionModelStore(prefs)

        val model = SubscriptionModel()
        model.id = "email1"
        model.type = SubscriptionType.EMAIL
        model.address = "user@example.com"
        store.add(model)

        model.address shouldBe "user@example.com"
    }

    test("persist hashes email but not push when both are present") {
        val prefs = MockPreferencesService()
        val store = SubscriptionModelStore(prefs)

        val pushModel = SubscriptionModel()
        pushModel.id = "push1"
        pushModel.type = SubscriptionType.PUSH
        pushModel.address = "fcm-token-abc123"
        store.add(pushModel)

        val emailModel = SubscriptionModel()
        emailModel.id = "email1"
        emailModel.type = SubscriptionType.EMAIL
        emailModel.address = "user@example.com"
        store.add(emailModel)

        val json = getPersistedJson(prefs)
        val models = (0 until json.length()).map { json.getJSONObject(it) }
        val pushJson = models.first { it.getString("type") == SubscriptionType.PUSH.toString() }
        val emailJson = models.first { it.getString("type") == SubscriptionType.EMAIL.toString() }

        pushJson.getString("address") shouldBe "fcm-token-abc123"
        emailJson.getString("address") shouldBe PIIHasher.hash("user@example.com")
        emailJson.getString("address") shouldMatch Regex("^[a-f0-9]{64}$")
    }
})
