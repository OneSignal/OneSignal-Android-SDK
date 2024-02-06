package com.onesignal.common.modeling

/**
 * A Map Model is a [MutableMap] that has a key of type string and a generically-specified
 * value.  It is a [Model] which hooks the [MutableMap] into the model framework and allows for change
 * notification propagation for any adds, removes, or updates to the [MutableMap].
 */
open class MapModel<V>(
    parentModel: Model? = null,
    parentProperty: String? = null,
) : Model(parentModel, parentProperty), MutableMap<String, V> {
    override val size: Int
        get() = data.size

    override val entries: MutableSet<MutableMap.MutableEntry<String, V>>
        get() = data.entries.filterIsInstance<MutableMap.MutableEntry<String, V>>().toMutableSet()

    override val keys: MutableSet<String>
        get() = data.keys

    override val values: MutableCollection<V>
        get() = data.values.map { it as V }.toMutableList()

    override fun containsKey(key: String): Boolean {
        return data.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        return data.containsValue(value)
    }

    override fun isEmpty(): Boolean {
        return data.isEmpty()
    }

    override fun get(key: String): V {
        return getOptAnyProperty(key) as V
    }

    override fun clear() {
        for (property in data.keys)
            setOptAnyProperty(property, null)
    }

    override fun put(
        key: String,
        value: V,
    ): V {
        setOptAnyProperty(key, value)
        return value
    }

    override fun putAll(from: Map<out String, V>) {
        for (item in from) {
            setOptAnyProperty(item.key, item.value)
        }
    }

    override fun remove(key: String): V {
        val value = getOptAnyProperty(key) as V
        setOptAnyProperty(key, null)
        return value
    }
}
