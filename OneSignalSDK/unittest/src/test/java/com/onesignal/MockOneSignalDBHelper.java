package com.onesignal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.onesignal.outcomes.data.OSOutcomeTableProvider;

public class MockOneSignalDBHelper implements OneSignalDb {

    private Context context;

    public MockOneSignalDBHelper(Context context) {
        this.context = context;
    }

    @Override
    public SQLiteDatabase getSQLiteDatabaseWithRetries() {
        return OneSignalDbHelper.getInstance(context).getSQLiteDatabaseWithRetries();
    }

    public void close() {
        OneSignalDbHelper.getInstance(context).close();
    }

}
