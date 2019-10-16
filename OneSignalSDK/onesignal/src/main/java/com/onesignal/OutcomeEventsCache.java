package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.WorkerThread;

import com.onesignal.OneSignalDbContract.OutcomeEventsTable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

class OutcomeEventsCache {

    private static final Object lock = new Object();

    /**
     * Delete event from the DB
     */
    @WorkerThread
    static void deleteOldOutcomeEvent(OutcomeEvent event, OneSignalDbHelper dbHelper) {
        synchronized (lock) {
            SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();

            try {
                writableDb.beginTransaction();
                writableDb.delete(OutcomeEventsTable.TABLE_NAME,
                        OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " = ?", new String[]{String.valueOf(event.getTimestamp())});
                writableDb.setTransactionSuccessful();
            } catch (Throwable t) {
                OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error deleting old outcome event records! ", t);
            } finally {
                if (writableDb != null) {
                    try {
                        writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                    } catch (Throwable t) {
                        OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", t);
                    }
                }
            }
        }
    }

    /**
     * Save an outcome event to send it on the future
     * <p>
     * For offline mode and contingency of errors
     */
    @WorkerThread
    static void saveOutcomeEvent(OutcomeEvent event, OneSignalDbHelper dbHelper) {
        synchronized (lock) {
            SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();
            String notificationIds = event.getNotificationIds() != null ? event.getNotificationIds().toString() : "[]";
            
            ContentValues values = new ContentValues();
            values.put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS, notificationIds);
            values.put(OutcomeEventsTable.COLUMN_NAME_SESSION, event.getSession().toString().toLowerCase());
            values.put(OutcomeEventsTable.COLUMN_NAME, event.getName());
            values.put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, event.getTimestamp());

            if (event.getParams() != null)
                values.put(OutcomeEventsTable.COLUMN_NAME_PARAMS, event.getParams());

            writableDb.insert(OutcomeEventsTable.TABLE_NAME, null, values);
            writableDb.close();
        }
    }

    /**
     * Save an outcome event to send it on the future
     * <p>
     * For offline mode and contingency of errors
     */
    @WorkerThread
    static List<OutcomeEvent> getAllEventsToSend(OneSignalDbHelper dbHelper) {
        List<OutcomeEvent> events = new ArrayList<>();

        synchronized (lock) {
            Cursor cursor = null;

            try {
                SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
                cursor = readableDb.query(
                        OutcomeEventsTable.TABLE_NAME,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                if (cursor.moveToFirst()) {
                    do {
                        String notificationIds = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS));
                        String name = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME));
                        String sessionString = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_SESSION));
                        OSSessionManager.Session session = OSSessionManager.Session.fromString(sessionString);
                        long timestamp = cursor.getLong(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP));

                        int paramsIndex = cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_PARAMS);
                        String paramsString = cursor.isNull(paramsIndex) ? null : cursor.getString(paramsIndex);
                        OutcomeParams params =
                                paramsString != null ? OutcomeParams.Builder
                                        .newInstance()
                                        .setJsonString(paramsString)
                                        .build() : null;

                        try {
                            OutcomeEvent event = new OutcomeEvent(session, new JSONArray(notificationIds), name, timestamp, params);
                            events.add(event);
                        } catch (JSONException e) {
                            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating JSONArray from notifications ids outcome:JSON Failed.", e);
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                if (cursor != null && !cursor.isClosed())
                    cursor.close();
            }
        }

        return events;
    }

}
