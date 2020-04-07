package com.onesignal;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.onesignal.outcomes.OSOutcomeTableProvider;

public class MockOneSignalDBHelper implements OneSignalDb {

    private Context context;

    public MockOneSignalDBHelper(Context context) {
        this.context = context;
    }

    public void setOutcomeTableProvider(OSOutcomeTableProvider outcomeTableProvider) {
        OneSignalDbHelper.getInstance(context).setOutcomeTableProvider(outcomeTableProvider);
    }

    @Override
    public SQLiteDatabase getWritableDbWithRetries() {
        return OneSignalDbHelper.getInstance(context).getWritableDbWithRetries();
    }

    @Override
    public SQLiteDatabase getReadableDbWithRetries() {
        return OneSignalDbHelper.getInstance(context).getReadableDbWithRetries();
    }

    public SQLiteDatabase getWritableDatabase() {
        return OneSignalDbHelper.getInstance(context).getWritableDatabase();
    }

    public SQLiteDatabase getReadableDatabase() {
        return OneSignalDbHelper.getInstance(context).getReadableDatabase();
    }

    public void cleanOutcomeDatabase() {
        OneSignalDbHelper.getInstance(context).cleanOutcomeDatabase();
    }

    public void close() {
        OneSignalDbHelper.getInstance(context).close();
    }

}
