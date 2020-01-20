package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

class OSInAppMessageRepository {

    private final OneSignalDbHelper dbHelper;

    OSInAppMessageRepository(OneSignalDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @WorkerThread
    synchronized void deleteInAppMessage(String messageId) {
        SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();

        try {
            writableDb.beginTransaction();
            writableDb.delete(OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID + " = ?", new String[]{messageId});
            writableDb.setTransactionSuccessful();
        } catch (Throwable t) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Error deleting in app message! ", t);
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

    @WorkerThread
    synchronized void saveInAppMessage(OSInAppMessage inAppMessage) {
        SQLiteDatabase writableDb = dbHelper.getWritableDbWithRetries();

        ContentValues values = new ContentValues();
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID, inAppMessage.messageId);
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY, inAppMessage.getDisplaysQuantity());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY, inAppMessage.getLastDisplayTime());

        int rowsUpdated = writableDb.update(OneSignalDbContract.InAppMessageTable.TABLE_NAME, values,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID + " = ?", new String[]{inAppMessage.messageId});
        if (rowsUpdated == 0)
            writableDb.insert(OneSignalDbContract.InAppMessageTable.TABLE_NAME, null, values);
        writableDb.close();
    }

    @WorkerThread
    synchronized OSInAppMessage getInAppMessage(String id) {
        Cursor cursor = null;
        String[] args = {id};

        try {
            SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
            cursor = readableDb.query(
                    OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    null,
                    OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID + "=?",
                    args,
                    null,
                    null,
                    null
            );

            if (cursor.moveToFirst()) {
                String messageId = cursor.getString(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));

                int displayQuantity = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY));
                long lastDisplay = cursor.getLong(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY));

                return new OSInAppMessage(messageId, displayQuantity, lastDisplay);
            }
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return null;
    }

    @WorkerThread
    synchronized List<OSInAppMessage> getAllInAppMessages() {
        List<OSInAppMessage> iams = new ArrayList<>();
        Cursor cursor = null;

        try {
            SQLiteDatabase readableDb = dbHelper.getReadableDbWithRetries();
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

                    int displayQuantity = cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY));
                    long lastDisplay = cursor.getLong(cursor.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY));

                    OSInAppMessage inAppMessage = new OSInAppMessage(messageId, displayQuantity, lastDisplay);
                    iams.add(inAppMessage);
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return iams;
    }

}
