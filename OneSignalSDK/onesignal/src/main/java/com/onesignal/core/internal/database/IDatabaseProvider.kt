package com.onesignal.core.internal.database

interface IDatabaseProvider {
    fun get(): IDatabase
}
