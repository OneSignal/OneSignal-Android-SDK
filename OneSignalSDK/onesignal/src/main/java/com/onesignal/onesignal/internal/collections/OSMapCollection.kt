package com.onesignal.onesignal.internal.collections

/**
 * A collection that is also a Map.  That is, each item in the collection also
 * contains a unique key that it can be indexed by.  Items of this collection
 * must implement [OSKeyedItem] to derive the key for each item.
 */
open class OSMapCollection<K, V>(items: Collection<V>) where V : OSKeyedItem<K> {
    protected val map = HashMap<K, V>()

    init {
        items.forEach {
            map[it.key] = it
        }
    }

    fun getAllAsMap() : Map<K, V> {
        return HashMap(map)
    }

    fun getAllAsCollection() : Collection<V> {
        return ArrayList(map.toList().map { it.second })
    }

    operator fun get(index: K) : V? {
        return map[index]
    }
}

interface OSKeyedItem<T> {
    val key: T
}