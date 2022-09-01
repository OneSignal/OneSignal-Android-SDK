package com.onesignal.core.internal.database

internal interface IDatabaseProvider {
    fun get(): IDatabase
}
