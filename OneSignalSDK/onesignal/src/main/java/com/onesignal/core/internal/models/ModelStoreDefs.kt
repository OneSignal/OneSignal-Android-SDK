package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.ModelStore
import com.onesignal.core.internal.modeling.SingletonModelStore

internal class ConfigModelStore : SingletonModelStore<ConfigModel>("config", { ConfigModel() }, ModelStore())
internal class SessionModelStore : SingletonModelStore<SessionModel>("session", { SessionModel() }, ModelStore())
internal class IdentityModelStore : SingletonModelStore<IdentityModel>("identity", { IdentityModel() }, ModelStore())
internal class PropertiesModelStore : SingletonModelStore<PropertiesModel>("properties", { PropertiesModel() }, ModelStore())
internal class SubscriptionModelStore : ModelStore<SubscriptionModel>()
internal class TriggerModelStore : ModelStore<TriggerModel>(persist = false)
