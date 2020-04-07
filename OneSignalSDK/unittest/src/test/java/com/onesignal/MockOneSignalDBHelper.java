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
    public SQLiteDatabase getSQLiteDatabaseWithRetries() {
        return OneSignalDbHelper.getInstance(context).getSQLiteDatabaseWithRetries();
    }

    public void close() {
        OneSignalDbHelper.getInstance(context).close();
    }

}
