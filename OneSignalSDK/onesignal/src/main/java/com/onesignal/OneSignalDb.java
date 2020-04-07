package com.onesignal;

import android.database.sqlite.SQLiteDatabase;

public interface OneSignalDb {

    SQLiteDatabase getWritableDbWithRetries();

    SQLiteDatabase getReadableDbWithRetries();

}
