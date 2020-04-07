package com.onesignal.outcomes;

public class MockOSOutcomeEventsTable extends OSOutcomesDbContract.OutcomeEventsTable {

    public static final String TABLE_NAME = OSOutcomesDbContract.OutcomeEventsTable.TABLE_NAME;
    // Influence ids
    public static final String COLUMN_NAME_NOTIFICATION_IDS = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS;
    public static final String COLUMN_NAME_IAM_IDS = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_IAM_IDS;
    // Influence type;
    public static final String COLUMN_NAME_SESSION = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_SESSION;
    public static final String COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE;
    public static final String COLUMN_NAME_IAM_INFLUENCE_TYPE = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE;
    // Outcome data
    public static final String COLUMN_NAME_NAME = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_NAME;
    public static final String COLUMN_NAME_WEIGHT = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_WEIGHT;
    public static final String COLUMN_NAME_TIMESTAMP = OSOutcomesDbContract.OutcomeEventsTable.COLUMN_NAME_TIMESTAMP;
}
