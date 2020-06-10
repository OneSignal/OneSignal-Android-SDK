package com.onesignal.outcomes;

import android.database.sqlite.SQLiteDatabase;

public class MockOSOutcomeTableProvider extends OSOutcomeTableProvider {
    private String mockedSqlCreateOutcomeEntries = null;
    private String mockedSqlCreateUniqueOutcomeEntries = null;

    @Override
    public String getSqlCreateOutcomeEntries() {
        if (mockedSqlCreateOutcomeEntries != null)
            return mockedSqlCreateOutcomeEntries;
        return super.getSqlCreateOutcomeEntries();
    }

    public void setMockedSqlCreateOutcomeEntries(String mockedSqlCreateOutcomeEntries) {
        this.mockedSqlCreateOutcomeEntries = mockedSqlCreateOutcomeEntries;
    }

    @Override
    public String getSqlCreateUniqueOutcomeEntries() {
        if (mockedSqlCreateUniqueOutcomeEntries != null)
            return mockedSqlCreateUniqueOutcomeEntries;
        return super.getSqlCreateUniqueOutcomeEntries();
    }

    public void setMockedSqlCreateUniqueOutcomeEntries(String mockedSqlCreateUniqueOutcomeEntries) {
        this.mockedSqlCreateUniqueOutcomeEntries = mockedSqlCreateUniqueOutcomeEntries;
    }

    @Override
    public void upgradeCacheOutcomeTableRevision1To2(SQLiteDatabase db) {
        super.upgradeCacheOutcomeTableRevision1To2(db);
    }

    @Override
    public void upgradeOutcomeTableRevision2To3(SQLiteDatabase db) {
        super.upgradeOutcomeTableRevision2To3(db);
    }
}
