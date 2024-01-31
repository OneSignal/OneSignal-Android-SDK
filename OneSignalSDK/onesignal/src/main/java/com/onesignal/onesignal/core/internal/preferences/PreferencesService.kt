package com.onesignal.onesignal.core.internal.preferences

import android.content.Context
import android.content.SharedPreferences
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.startup.IStartableService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED


class PreferencesService(
    private val _applicationService: IApplicationService,
    private val _time: ITime
) : IPreferencesService, IStartableService {
    private val _prefsToApply: Map<String, MutableMap<String, Any?>> = mapOf(
        PreferenceStores.ONESIGNAL to mutableMapOf(),
        PreferenceStores.PLAYER_PURCHASES to mutableMapOf(),
        PreferenceStores.TRIGGERS to mutableMapOf()
    )
    private var _queueJob: Deferred<Unit>? = null

    // Use a channel to ensure the IO worker is woken up.  We don't care about the value, and we
    // use CONFLATED to not wake up the worker more than needed.
    private val _conflatedChannel = Channel<Any?>(CONFLATED)

    override fun start() {
        // fire up an async job that will run "forever" so we don't hold up the other startable services.
        _queueJob = doWorkAsync()
    }

    override fun getString(store: String, key: String, defValue: String?) : String? = get(store, key, String::class.java, defValue) as String?
    override fun getBool(store: String, key: String, defValue: Boolean?) : Boolean? = get(store, key, Boolean::class.java, defValue) as Boolean?
    override fun getInt(store: String, key: String, defValue: Int?) : Int? = get(store, key, Int::class.java, defValue) as Int?
    override fun getLong(store: String, key: String, defValue: Long?) : Long? = get(store, key, Long::class.java, defValue) as Long?
    override fun getStringSet(store: String, key: String, defValue: Set<String>?) : Set<String>? = get(store, key, Set::class.java, defValue) as Set<String>?

    override fun saveString(store: String, key: String, value: String?) = save(store, key, value)
    override fun saveBool(store: String, key: String, value: Boolean?) = save(store, key, value)
    override fun saveInt(store: String, key: String, value: Int?) = save(store, key, value)
    override fun saveLong(store: String, key: String, value: Long?) = save(store, key, value)
    override fun saveStringSet(store: String, key: String, value: Set<String>?) = save(store, key, value)

    private fun get(store: String, key: String, type: Class<*>, defValue: Any?) : Any? {
        if(!_prefsToApply.containsKey(store))
            throw Exception("Store not found: $store")

        val storeMap = _prefsToApply[store]!!

        synchronized(storeMap) {
            val cachedValue = storeMap[key]
            if (cachedValue != null || storeMap.containsKey(key))
                return cachedValue
        }

        val prefs = getSharedPrefsByName(store)
        if (prefs != null) {
            return when (type) {
                String::class.java -> prefs.getString(key, defValue as String?)
                Boolean::class.java -> prefs.getBoolean(key, (defValue as Boolean?) ?: false)
                Int::class.java -> prefs.getInt(key, (defValue as Int?) ?: 0)
                Long::class.java -> prefs.getLong(key, (defValue as Long?) ?: 0)
                Set::class.java -> prefs.getStringSet(key, defValue as Set<String>?)
                else -> null
            }
        }

        return defValue
    }

    private fun save(store: String, key: String, value: Any?) {
        if(!_prefsToApply.containsKey(store))
            throw Exception("Store not found: $store")

        val storeMap = _prefsToApply[store]!!
        synchronized(storeMap) {
            storeMap[key] = value
        }

        runBlocking { _conflatedChannel.send(null) }
    }

    private fun doWorkAsync() = GlobalScope.async(Dispatchers.IO) {
        try {
            var lastSyncTime = _time.currentTimeMillis

            while(true) {
                // go through all outstanding items to process
                for (storeKey in _prefsToApply.keys) {
                    val storeMap = _prefsToApply[storeKey]!!
                    val prefsToWrite = getSharedPrefsByName(storeKey)

                    if(prefsToWrite == null) {
                        // the assumption here is there is no context yet, but will be. So ensure
                        // we wake up to try again and persist the preference.
                        _conflatedChannel.send(null)
                        continue
                    }

                    val editor = prefsToWrite!!.edit()

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

                if(delay > 0)
                    delay(delay)

                // wait to be woken up for the next pass
                _conflatedChannel.receive()
            }
        } catch (e: Throwable) {
            Logging.log(LogLevel.ERROR, "Error with Preference work loop", e)
            // TODO: Restart/crash logic
        }
    }

    @Synchronized
    private fun getSharedPrefsByName(store: String): SharedPreferences? {
        val context = _applicationService.appContext
        if (context == null) {
            Logging.warn("OneSignal.appContext null, could not read $store from getSharedPreferences.")
            return null
        }

        return context.getSharedPreferences(store, Context.MODE_PRIVATE)
    }

    companion object {
        private const val WRITE_CALL_DELAY_TO_BUFFER_MS = 200
    }
}