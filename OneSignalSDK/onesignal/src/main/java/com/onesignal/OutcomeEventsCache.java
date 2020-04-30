package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.support.annotation.WorkerThread;

import com.onesignal.OneSignalDbContract.OutcomeEventsTable;
import com.onesignal.OneSignalDbContract.CachedUniqueOutcomeNotificationTable;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

class OutcomeEventsCache {

    /**
     * Delete event from the DB
     */
    @WorkerThread
    synchronized static void deleteOldOutcomeEvent(OutcomeEvent event, OneSignalDbHelper dbHelper) {
        SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();

        try {
            writableDb.beginTransaction();
            writableDb.delete(OutcomeEventsTable.TABLE_NAME,
                    OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " = ?", new String[]{String.valueOf(event.getTimestamp())});
            writableDb.setTransactionSuccessful();
        } catch (SQLiteException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error deleting old outcome event records! ", e);
        } finally {
            if (writableDb != null) {
                try {
                    writableDb.endTransaction(); // May throw if transaction was never opened or DB is full.
                } catch (SQLiteException e) {
                    OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error closing transaction! ", e);
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
    synchronized static void saveOutcomeEvent(OutcomeEvent event, OneSignalDbHelper dbHelper) {
        SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();
        String notificationIds = event.getNotificationIds() != null ? event.getNotificationIds().toString() : "[]";

        ContentValues values = new ContentValues();
        values.put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS, notificationIds);
        values.put(OutcomeEventsTable.COLUMN_NAME_SESSION, event.getSession().toString().toLowerCase());
        values.put(OutcomeEventsTable.COLUMN_NAME_NAME, event.getName());
        values.put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, event.getTimestamp());
        values.put(OutcomeEventsTable.COLUMN_NAME_WEIGHT, event.getWeight());

        writableDb.insert(OutcomeEventsTable.TABLE_NAME, null, values);
    }

    /**
     * Save an outcome event to send it on the future
     * <p>
     * For offline mode and contingency of errors
     */
    @WorkerThread
    synchronized static List<OutcomeEvent> getAllEventsToSend(OneSignalDbHelper dbHelper) {
        List<OutcomeEvent> events = new ArrayList<>();
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
                    String sessionString = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_SESSION));
                    OSSessionManager.Session session = OSSessionManager.Session.fromString(sessionString);
                    String notificationIds = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS));
                    String name = cursor.getString(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NAME));
                    long timestamp = cursor.getLong(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP));
                    float weight = cursor.getFloat(cursor.getColumnIndex(OutcomeEventsTable.COLUMN_NAME_WEIGHT));

                    try {
                        OutcomeEvent event = new OutcomeEvent(session, new JSONArray(notificationIds), name, timestamp, weight);
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

        return events;
    }

    /**
     * Save a JSONArray of notification ids as separate items with the unique outcome name
     */
    @WorkerThread
    synchronized static void saveUniqueOutcomeNotifications(JSONArray notificationIds, String outcomeName, OneSignalDbHelper dbHelper) {
        if (notificationIds == null)
            return;

        SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();
        try {
            for (int i = 0; i < notificationIds.length(); i++) {
                ContentValues values = new ContentValues();

                String notificationId = notificationIds.getString(i);
                values.put(CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID, notificationId);
                values.put(CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NAME, outcomeName);

                writableDb.insert(CachedUniqueOutcomeNotificationTable.TABLE_NAME, null, values);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    @WorkerThread
    synchronized static JSONArray getNotCachedUniqueOutcomeNotifications(String name, JSONArray notificationIds, OneSignalDbHelper dbHelper) {
        JSONArray uniqueNotificationIds = new JSONArray();

        SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
        Cursor cursor = null;

        try {
            for (int i = 0; i < notificationIds.length(); i++) {
                String notificationId = notificationIds.getString(i);
                CachedUniqueOutcomeNotification notification = new CachedUniqueOutcomeNotification(notificationId, name);

                String[] columns = new String[]{};

                String where = CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NOTIFICATION_ID +  " = ? AND " +
                        CachedUniqueOutcomeNotificationTable.COLUMN_NAME_NAME +  " = ?";

                String[] args = new String[]{notification.getNotificationId(), notification.getName()};

                cursor = readableDb.query(
                        OneSignalDbContract.CachedUniqueOutcomeNotificationTable.TABLE_NAME,
                        columns,
                        where,
                        args,
                        null,
                        null,
                        null,
                        "1"
                );

                // Item is not cached, add it to the JSONArray
                if (cursor.getCount() == 0)
                    uniqueNotificationIds.put(notificationId);

            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return uniqueNotificationIds;
    }
}
