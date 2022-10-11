package com.onesignal.core.internal.database

import android.content.ContentValues

/**
 * Allows for the abstract CRUD operations of an underlying database. This is a light abstraction
 * over the database.  The operations are synchronous on the calling thread, it is up to the
 * caller to offload data operations to a non-main thread.
 */
internal interface IDatabase {

    /**
     * Query for the underlying data.
     *
     * @param table The table to query.
     * @param columns The columns to retrieve. Provide `null` to query all columns.
     * @param whereClause The row selection criteria (i.e. the WHERE portion). Parameterizing values
     * and providing the parameters within the [whereArgs] is recommended. Provide `null` to
     * retrieve all rows.
     * @param whereArgs The row selection criteria arguments.  Provide `null` when there were
     * no parameters provided in [whereClause].
     * @param groupBy The group by criteria. Provide `null` when there is no criteria.
     * @param having The having criteria. Provide `null` when there is no criteria.
     * @param orderBy The order by criteria. Provide `null` when there is no criteria.
     * @param limit The limit criteria. Provide `null` when there is no criteria.
     * @param action The lambda that will be executed, allowing the caller to process the result of the query.
     * It is structured this way so the caller does not have to worry about closing resources.
     */
    fun query(
        table: String,
        columns: Array<String>? = null,
        whereClause: String? = null,
        whereArgs: Array<String>? = null,
        groupBy: String? = null,
        having: String? = null,
        orderBy: String? = null,
        limit: String? = null,
        action: (ICursor) -> Unit
    )

    /**
     * Insert a new record into the database as specified. If the insert fails, it will fail silently.
     *
     * @param table The table to insert data into.
     * @param nullColumnHack The null column hack criteria.
     * @param values The values that should be inserted into the [table].
     *
     * @see [https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#insert(java.lang.String,%20java.lang.String,%20android.content.ContentValues)]
     */
    fun insert(table: String, nullColumnHack: String?, values: ContentValues?)

    /**
     * Insert a new record into the database as specified. If the insert fails, it will throw an exception.
     *
     * @param table The table to insert data into.
     * @param nullColumnHack The null column hack criteria.
     * @param values The values that should be inserted into the [table].
     *
     * @see [https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#insert(java.lang.String,%20java.lang.String,%20android.content.ContentValues)]
     */
    fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues?)

    /**
     * Update one or more records into the database as specified.
     *
     * @param table The table to insert data into.
     * @param values The values that should be inserted into the [table].
     * @param whereClause The row selection criteria (i.e. the WHERE portion). Parameterizing values
     * and providing the parameters within the [whereArgs] is recommended. Provide `null` to
     * retrieve all rows.
     * @param whereArgs The row selection criteria arguments.  Provide `null` when there were
     * no parameters provided in [whereClause].
     *
     * @return The number of records that were updated.
     */
    fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int

    /**
     * Delete one or more records from the database as specified.
     *
     * @param table The table to insert data into.
     * @param whereClause The row selection criteria (i.e. the WHERE portion). Parameterizing values
     * and providing the parameters within the [whereArgs] is recommended. Provide `null` to
     * retrieve all rows.
     * @param whereArgs The row selection criteria arguments.  Provide `null` when there were
     * no parameters provided in [whereClause].
     */
    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?)
}
