package com.onesignal.outcomes;

import android.database.sqlite.SQLiteDatabase;

public class MockOSOutcomeTableProvider extends OSOutcomeTableProvider {

    private boolean upgradeAvailable = true;
    private String mockedSqlCreateOutcomeEntries = null;
    private String mockedSqlCreateUniqueOutcomeEntries = null;

    public void clean() {
        mockedSqlCreateOutcomeEntries = null;
        mockedSqlCreateUniqueOutcomeEntries = null;
        upgradeAvailable = true;
    }

    public void disableUpgrade() {
        upgradeAvailable = false;
    }

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
        if (upgradeAvailable)
            super.upgradeCacheOutcomeTableRevision1To2(db);
    }

    @Override
    public void upgradeOutcomeTableRevision2To3(SQLiteDatabase db) {
        if (upgradeAvailable)
            super.upgradeOutcomeTableRevision2To3(db);
    }
}
