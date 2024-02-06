package com.onesignal.core.internal.preferences.impl

import android.content.Context
import android.content.SharedPreferences
import com.onesignal.common.threading.Waiter
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

internal class PreferencesService(
    private val _applicationService: IApplicationService,
    private val _time: ITime,
) : IPreferencesService, IStartableService {
    private val prefsToApply: Map<String, MutableMap<String, Any?>> =
        mapOf(
            PreferenceStores.ONESIGNAL to mutableMapOf(),
            PreferenceStores.PLAYER_PURCHASES to mutableMapOf(),
        )
    private var queueJob: Deferred<Unit>? = null

    private val waiter = Waiter()

    override fun start() {
        // fire up an async job that will run "forever" so we don't hold up the other startable services.
        queueJob = doWorkAsync()
    }

    override fun getString(
        store: String,
        key: String,
        defValue: String?,
    ): String? =
        get(
            store,
            key,
            String::class.java,
            defValue,
        ) as String?

    override fun getBool(
        store: String,
        key: String,
        defValue: Boolean?,
    ): Boolean? =
        get(
            store,
            key,
            Boolean::class.java,
            defValue,
        ) as Boolean?

    override fun getInt(
        store: String,
        key: String,
        defValue: Int?,
    ): Int? = get(store, key, Int::class.java, defValue) as Int?

    override fun getLong(
        store: String,
        key: String,
        defValue: Long?,
    ): Long? = get(store, key, Long::class.java, defValue) as Long?

    override fun getStringSet(
        store: String,
        key: String,
        defValue: Set<String>?,
    ): Set<String>? =
        get(
            store,
            key,
            Set::class.java,
            defValue,
        ) as Set<String>?

    override fun saveString(
        store: String,
        key: String,
        value: String?,
    ) = save(store, key, value)

    override fun saveBool(
        store: String,
        key: String,
        value: Boolean?,
    ) = save(store, key, value)

    override fun saveInt(
        store: String,
        key: String,
        value: Int?,
    ) = save(store, key, value)

    override fun saveLong(
        store: String,
        key: String,
        value: Long?,
    ) = save(store, key, value)

    override fun saveStringSet(
        store: String,
        key: String,
        value: Set<String>?,
    ) = save(store, key, value)

    private fun get(
        store: String,
        key: String,
        type: Class<*>,
        defValue: Any?,
    ): Any? {
        if (!prefsToApply.containsKey(store)) {
            throw Exception("Store not found: $store")
        }

        val storeMap = prefsToApply[store]!!

        synchronized(storeMap) {
            val cachedValue = storeMap[key]
            if (cachedValue != null || storeMap.containsKey(key)) {
                return cachedValue
            }
        }

        val prefs = getSharedPrefsByName(store)
        if (prefs != null) {
            try {
                return when (type) {
                    String::class.java -> prefs.getString(key, defValue as String?)
                    Boolean::class.java -> prefs.getBoolean(key, (defValue as Boolean?) ?: false)
                    Int::class.java -> prefs.getInt(key, (defValue as Int?) ?: 0)
                    Long::class.java -> prefs.getLong(key, (defValue as Long?) ?: 0)
                    Set::class.java -> prefs.getStringSet(key, defValue as Set<String>?)
                    else -> null
                }
            } catch (ex: Exception) {
                // any issues retrieving the preference, return the default value.
            }
        }

        return when (type) {
            String::class.java -> defValue as String?
            Boolean::class.java -> (defValue as Boolean?) ?: false
            Int::class.java -> (defValue as Int?) ?: 0
            Long::class.java -> (defValue as Long?) ?: 0
            Set::class.java -> defValue as Set<String>?
            else -> null
        }
    }

    private fun save(
        store: String,
        key: String,
        value: Any?,
    ) {
        if (!prefsToApply.containsKey(store)) {
            throw Exception("Store not found: $store")
        }

        val storeMap = prefsToApply[store]!!
        synchronized(storeMap) {
            storeMap[key] = value
        }

        waiter.wake()
    }

    private fun doWorkAsync() =
        GlobalScope.async(Dispatchers.IO) {
            var lastSyncTime = _time.currentTimeMillis

            while (true) {
                try {
                    // go through all outstanding items to process
                    for (storeKey in prefsToApply.keys) {
                        val storeMap = prefsToApply[storeKey]!!
                        val prefsToWrite = getSharedPrefsByName(storeKey)

                        if (prefsToWrite == null) {
                            // the assumption here is there is no context yet, but will be. So ensure
                            // we wake up to try again and persist the preference.
                            waiter.wake()
                            continue
                        }

                        val editor = prefsToWrite.edit()

                        synchronized(storeMap) {
                            for (key in storeMap.keys) {
                                when (val value = storeMap[key]) {
                                    is String -> editor.putString(key, value as String?)
                                    is Boolean -> editor.putBoolean(key, (value as Boolean?)!!)
                                    is Int -> editor.putInt(key, (value as Int?)!!)
                                    is Long -> editor.putLong(key, (value as Long?)!!)
                                    is Set<*> -> editor.putStringSet(key, value as Set<String?>?)
                                    null -> editor.remove(key)
                                }
                            }
                            storeMap.clear()
                        }
                        editor.apply()
                    }

                    // potentially delay to prevent this from constant IO if a bunch of
                    // preferences are set sequentially.
                    val newTime = _time.currentTimeMillis

                    val delay = lastSyncTime - newTime + WRITE_CALL_DELAY_TO_BUFFER_MS
                    lastSyncTime = newTime

                    if (delay > 0) {
                        delay(delay)
                    }

                    // wait to be woken up for the next pass
                    waiter.waitForWake()
                } catch (e: Throwable) {
                    Logging.log(LogLevel.ERROR, "Error with Preference work loop", e)
                }
            }
        }

    @Synchronized
    private fun getSharedPrefsByName(store: String): SharedPreferences? {
        return _applicationService.appContext.getSharedPreferences(store, Context.MODE_PRIVATE)
    }

    companion object {
        private const val WRITE_CALL_DELAY_TO_BUFFER_MS = 200
    }
}
