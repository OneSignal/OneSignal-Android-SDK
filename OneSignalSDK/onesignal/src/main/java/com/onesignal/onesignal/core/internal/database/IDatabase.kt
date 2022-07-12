package com.onesignal.onesignal.core.internal.database

import android.content.ContentValues
import android.database.Cursor
import android.database.SQLException

interface IDatabase {
    fun query(
        table: String, columns: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, groupBy: String?, having: String?,
        orderBy: String?
    ): Cursor

    fun query(
        table: String, columns: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, groupBy: String?, having: String?,
        orderBy: String?, limit: String?
    ): Cursor

    fun insert(table: String, nullColumnHack: String?, values: ContentValues?)

    @Throws(SQLException::class)
    fun insertOrThrow(table: String, nullColumnHack: String?, values: ContentValues?)
    fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?
    ): Int

    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?)
}