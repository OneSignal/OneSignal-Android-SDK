package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public interface OneSignalDb {

    Cursor query(@NonNull String table, @Nullable String[] columns, @Nullable String selection,
                 String[] selectionArgs, @Nullable String groupBy, @Nullable String having,
                 @Nullable String orderBy);

    Cursor query(@NonNull String table, @Nullable String[] columns, @Nullable String selection,
                 @Nullable String[] selectionArgs, @Nullable String groupBy, @Nullable String having,
                 @Nullable String orderBy, @Nullable String limit);

    void insert(@NonNull String table, @Nullable String nullColumnHack, @Nullable ContentValues values);

    void insertOrThrow(@NonNull String table, @Nullable String nullColumnHack, @Nullable ContentValues values)
            throws SQLException;

    int update(@NonNull String table, @NonNull ContentValues values, @Nullable String whereClause, @Nullable String[] whereArgs);

    void delete(@NonNull String table, @Nullable String whereClause, @Nullable String[] whereArgs);
}
