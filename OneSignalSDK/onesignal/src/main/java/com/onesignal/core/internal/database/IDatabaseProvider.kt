package com.onesignal.core.internal.database

/**
 * The database provider provides access to the [IDatabase] instances that
 * are managed by the OneSignal SDK.
 */
internal interface IDatabaseProvider {
    /**
     * The OneSignal database.
     */
    val os: IDatabase
}
