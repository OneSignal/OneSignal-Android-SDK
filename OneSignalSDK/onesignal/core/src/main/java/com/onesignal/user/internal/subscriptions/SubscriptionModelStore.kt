package com.onesignal.user.internal.subscriptions

import com.onesignal.common.PIIHasher
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import org.json.JSONObject

open class SubscriptionModelStore(prefs: IPreferencesService) : SimpleModelStore<SubscriptionModel>({
    SubscriptionModel()
}, "subscriptions", prefs) {
    override fun replaceAll(
        models: List<SubscriptionModel>,
        tag: String,
    ) {
        if (tag != ModelChangeTags.HYDRATE) {
            return super.replaceAll(models, tag)
        }
        // When hydrating an existing push subscription model, use existing device properties
        synchronized(models) {
            for (model in models) {
                if (model.type == SubscriptionType.PUSH) {
                    val existingPushModel = get(model.id)
                    if (existingPushModel != null) {
                        model.sdk = existingPushModel.sdk
                        model.deviceOS = existingPushModel.deviceOS
                        model.carrier = existingPushModel.carrier
                        model.appVersion = existingPushModel.appVersion
                        model.status = existingPushModel.status
                    }
                    break
                }
            }
            super.replaceAll(models, tag)
        }
    }

    override fun transformJsonForPersistence(
        model: SubscriptionModel,
        json: JSONObject,
    ): JSONObject {
        if (model.type == SubscriptionType.PUSH) return json

        val address = json.optString("address", "")
        if (address.isNotEmpty() && !PIIHasher.isHashed(address)) {
            json.put("address", PIIHasher.hash(address))
        }
        return json
    }
}
