package com.onesignal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.os.SystemClock;

import com.onesignal.outcomes.OSOutcomeTableProvider;

public class MockOneSignalDBHelper implements OneSignalDb {
    private static final int DB_OPEN_RETRY_MAX = 5;
    private static final int DB_OPEN_RETRY_BACKOFF = 400;

    private Context context;

    public MockOneSignalDBHelper(Context context) {
        this.context = context;
    }

    public void close() {
        OneSignalDbHelper.getInstance(context).close();
    }

    public SQLiteDatabase getSQLiteDatabaseWithRetries() {
        int count = 0;
        while (true) {
            try {
                return OneSignalDbHelper.getInstance(context).getWritableDatabase();
            } catch (SQLiteCantOpenDatabaseException | SQLiteDatabaseLockedException e) {
                if (++count >= DB_OPEN_RETRY_MAX)
                    throw e;
                SystemClock.sleep(count * DB_OPEN_RETRY_BACKOFF);
            }
        }
    }

    @Override
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy) {
        return OneSignalDbHelper.getInstance(context).query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    }

    @Override
    public Cursor query(String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
        return OneSignalDbHelper.getInstance(context).query(table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
    }

    @Override
    public void insert(String table, String nullColumnHack, ContentValues values) {
        OneSignalDbHelper.getInstance(context).insert(table, nullColumnHack, values);
    }

    @Override
    public void insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
        OneSignalDbHelper.getInstance(context).insertOrThrow(table, nullColumnHack, values);
    }

    @Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return OneSignalDbHelper.getInstance(context).update(table, values, whereClause, whereArgs);
    }

    @Override
    public void delete(String table, String whereClause, String[] whereArgs) {
        OneSignalDbHelper.getInstance(context).delete(table, whereClause, whereArgs);
    }
}
