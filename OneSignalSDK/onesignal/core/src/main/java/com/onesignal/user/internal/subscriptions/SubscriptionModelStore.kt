package com.onesignal.user.internal.subscriptions

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

open class SubscriptionModelStore(prefs: IPreferencesService) : SimpleModelStore<SubscriptionModel>({ SubscriptionModel() }, "subscriptions", prefs)
