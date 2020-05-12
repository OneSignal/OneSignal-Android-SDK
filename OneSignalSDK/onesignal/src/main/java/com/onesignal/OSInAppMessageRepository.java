package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class OSInAppMessageRepository {

    private final OneSignalDbHelper dbHelper;

    OSInAppMessageRepository(OneSignalDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @WorkerThread
    synchronized void saveInAppMessage(OSInAppMessage inAppMessage) {
        SQLiteDatabase writableDb = dbHelper.getSQLiteDatabaseWithRetries();

        ContentValues values = new ContentValues();
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID, inAppMessage.messageId);
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY, inAppMessage.getRedisplayStats().getDisplayQuantity());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY, inAppMessage.getRedisplayStats().getLastDisplayTime());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS, inAppMessage.getClickedClickIds().toString());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION, inAppMessage.isDisplayedInSession());

        int rowsUpdated = writableDb.update(OneSignalDbContract.InAppMessageTable.TABLE_NAME, values,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID + " = ?", new String[]{inAppMessage.messageId});
        if (rowsUpdated == 0)
            writableDb.insert(OneSignalDbContract.InAppMessageTable.TABLE_NAME, null, values);
    }

    @WorkerThread
    synchronized List<OSInAppMessage> getCachedInAppMessages() {
        List<OSInAppMessage> iams = new ArrayList<>();
        Cursor cursor = null;

        try {
            SQLiteDatabase readableDb = dbHelper.getSQLiteDatabaseWithRetries();
            cursor = readableDb.query(
                    OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );

            if (cursor.moveToFirst()) {
                do {
                    String messageId = cursor.getString(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));
                    String clickIds = cursor.getString(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS));
                    int displayQuantity = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY));
                    long lastDisplay = cursor.getLong(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY));
                    boolean displayed = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION)) == 1;

                    Set<String> clickIdsSet = OSUtils.newStringSetFromJSONArray(new JSONArray(clickIds));

                    OSInAppMessage inAppMessage = new OSInAppMessage(messageId, clickIdsSet, displayed, new OSInAppMessageRedisplayStats(displayQuantity, lastDisplay));
                    iams.add(inAppMessage);
                } while (cursor.moveToNext());
            }
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating JSONArray from iam click ids:JSON Failed.", e);
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return iams;
    }

}
