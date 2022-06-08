package com.onesignal.onesignal.internal.collections

open class OSCollection<T>(
        protected val collection: Collection<T>
) {
    fun asCollection() : Collection<T> {
        return ArrayList(collection);
    }
}
