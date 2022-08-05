package com.onesignal.onesignal.core.internal.models

import com.onesignal.onesignal.core.internal.modeling.ModelStore
import com.onesignal.onesignal.core.internal.modeling.SingletonModelStore

internal class ConfigModelStore : SingletonModelStore<ConfigModel>("config", { ConfigModel() }, ModelStore() )
internal class SessionModelStore : SingletonModelStore<SessionModel>("session", { SessionModel() }, ModelStore() )
internal class IdentityModelStore: ModelStore<IdentityModel>()
internal class PropertiesModelStore: ModelStore<PropertiesModel>()
internal class SubscriptionModelStore: ModelStore<SubscriptionModel>()
internal class TriggerModelStore: ModelStore<TriggerModel>()