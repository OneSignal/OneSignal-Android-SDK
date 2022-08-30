package com.onesignal.onesignal.core.internal.database

interface IDatabaseProvider {
    fun get(): IDatabase
}
