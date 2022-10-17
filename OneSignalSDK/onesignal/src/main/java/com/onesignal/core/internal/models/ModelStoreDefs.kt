package com.onesignal.core.internal.models

import com.onesignal.core.internal.modeling.SimpleModelStore
import com.onesignal.core.internal.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

internal open class ConfigModelStore(prefs: IPreferencesService) : SingletonModelStore<ConfigModel>(SimpleModelStore({ ConfigModel() }, "config", prefs))
internal open class SessionModelStore(prefs: IPreferencesService) : SingletonModelStore<SessionModel>(SimpleModelStore({ SessionModel() }, "session", prefs))
internal open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(SimpleModelStore({ IdentityModel() }, "identity", prefs))
internal open class PropertiesModelStore(prefs: IPreferencesService) : SingletonModelStore<PropertiesModel>(SimpleModelStore({ PropertiesModel() }, "properties", prefs))
internal open class SubscriptionModelStore(prefs: IPreferencesService) : SimpleModelStore<SubscriptionModel>({ SubscriptionModel() }, "subscriptions", prefs)
internal open class TriggerModelStore : SimpleModelStore<TriggerModel>({ TriggerModel() })
