package com.onesignal.core.internal.database

/**
 * The data cursor that is provided on the [IDatabase.query] lambda. It provides access
 * to the data retrieved by the query.
 */
interface ICursor {
    /**
     * The number of records this cursor can traverse.
     */
    val count: Int

    /**
     * Move the cursor to the first record in the result set.
     *
     * @return True if there is a first record, false otherwise.
     */
    fun moveToFirst(): Boolean

    /**
     * Move the cursor to the next record in the result set.
     *
     * @return True if there is a next record, false otherwise.
     */
    fun moveToNext(): Boolean

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [String] value of the column.
     */
    fun getString(column: String): String

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [Float] value of the column.
     */
    fun getFloat(column: String): Float

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [Long] value of the column.
     */
    fun getLong(column: String): Long

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [Int] value of the column.
     */
    fun getInt(column: String): Int

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [String] value of the column, or null if it is `null`.
     */
    fun getOptString(column: String): String?

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [Float] value of the column, or null if it is `null`.
     */
    fun getOptFloat(column: String): Float?

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [Long] value of the column, or null if it is `null`.
     */
    fun getOptLong(column: String): Long?

    /**
     * Retrieve the value of the provided column from the current record.
     *
     * @param column The name of the column to retrieve.
     *
     * @return The [Int] value of the column, or null if it is `null`.
     */
    fun getOptInt(column: String): Int?
}
