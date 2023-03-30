package com.onesignal.core.internal.database.impl

import android.database.Cursor
import com.onesignal.core.internal.database.ICursor

internal class DatabaseCursor(
    private val _cursor: Cursor,
) : ICursor {
    override val count: Int get() = _cursor.count
    override fun moveToFirst(): Boolean = _cursor.moveToFirst()
    override fun moveToNext(): Boolean = _cursor.moveToNext()
    override fun getString(column: String): String = _cursor.getString(_cursor.getColumnIndex(column))
    override fun getFloat(column: String): Float = _cursor.getFloat(_cursor.getColumnIndex(column))
    override fun getLong(column: String): Long = _cursor.getLong(_cursor.getColumnIndex(column))
    override fun getInt(column: String): Int = _cursor.getInt(_cursor.getColumnIndex(column))

    override fun getOptString(column: String): String? {
        val idx = _cursor.getColumnIndex(column)
        if (_cursor.isNull(idx)) {
            return null
        }

        return _cursor.getString(idx)
    }

    override fun getOptFloat(column: String): Float? {
        val idx = _cursor.getColumnIndex(column)
        if (_cursor.isNull(idx)) {
            return null
        }

        return _cursor.getFloat(idx)
    }

    override fun getOptLong(column: String): Long? {
        val idx = _cursor.getColumnIndex(column)
        if (_cursor.isNull(idx)) {
            return null
        }

        return _cursor.getLong(idx)
    }

    override fun getOptInt(column: String): Int? {
        val idx = _cursor.getColumnIndex(column)
        if (_cursor.isNull(idx)) {
            return null
        }

        return _cursor.getInt(idx)
    }
}
