package com.onesignal.mocks

import com.onesignal.core.internal.preferences.IPreferencesService

/**
 * The mock preferences store allows the initialization of the preferences store values as needed,
 * and will return any saved value in memory.
 */
internal class MockPreferencesService(map: Map<String, Any?>? = null) : IPreferencesService {
    private val _map: MutableMap<String, Any?> = map?.toMutableMap() ?: mutableMapOf()

    override fun getString(store: String, key: String, defValue: String?): String? = (_map[key] as String?) ?: defValue
    override fun getBool(store: String, key: String, defValue: Boolean?): Boolean? = (_map[key] as Boolean?) ?: defValue
    override fun getInt(store: String, key: String, defValue: Int?): Int? = (_map[key] as Int?) ?: defValue
    override fun getLong(store: String, key: String, defValue: Long?): Long? = (_map[key] as Long?) ?: defValue
    override fun getStringSet(store: String, key: String, defValue: Set<String>?): Set<String>? = (_map[key] as Set<String>?) ?: defValue

    override fun saveString(store: String, key: String, value: String?) { _map[key] = value }
    override fun saveBool(store: String, key: String, value: Boolean?) { _map[key] = value }
    override fun saveInt(store: String, key: String, value: Int?) { _map[key] = value }
    override fun saveLong(store: String, key: String, value: Long?) { _map[key] = value }
    override fun saveStringSet(store: String, key: String, value: Set<String>?) { _map[key] = value }
}
