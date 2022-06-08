package com.onesignal.user.collections

open class OSCollection<T>(
        protected val collection: Collection<T>
) {
    fun asCollection() : Collection<T> {
        return ArrayList(collection);
    }
}
