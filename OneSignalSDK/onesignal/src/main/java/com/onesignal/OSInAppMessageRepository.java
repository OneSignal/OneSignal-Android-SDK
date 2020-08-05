package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;
import android.support.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class OSInAppMessageRepository {

    final static long IAM_CACHE_DATA_LIFETIME = 15_552_000L; // 6 months in seconds

    private final OneSignalDbHelper dbHelper;

    OSInAppMessageRepository(OneSignalDbHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    @WorkerThread
    synchronized void saveInAppMessage(OSInAppMessage inAppMessage) {
        ContentValues values = new ContentValues();
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID, inAppMessage.messageId);
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY, inAppMessage.getRedisplayStats().getDisplayQuantity());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY, inAppMessage.getRedisplayStats().getLastDisplayTime());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS, inAppMessage.getClickedClickIds().toString());
        values.put(OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION, inAppMessage.isDisplayedInSession());

        int rowsUpdated = dbHelper.update(OneSignalDbContract.InAppMessageTable.TABLE_NAME, values,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID + " = ?", new String[]{inAppMessage.messageId});
        if (rowsUpdated == 0)
            dbHelper.insert(OneSignalDbContract.InAppMessageTable.TABLE_NAME, null, values);
    }

    @WorkerThread
    synchronized List<OSInAppMessage> getCachedInAppMessages() {
        List<OSInAppMessage> inAppMessages = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = dbHelper.query(
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
                    inAppMessages.add(inAppMessage);
                } while (cursor.moveToNext());
            }
        } catch (JSONException e) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "Generating JSONArray from iam click ids:JSON Failed.", e);
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }

        return inAppMessages;
    }

    @WorkerThread
    synchronized void cleanCachedInAppMessages() {
        // 1. Query for all old message ids and old clicked click ids
        String[] retColumns = new String[]{
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID,
                OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS
        };

        String whereStr = OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY + " < ?";

        String sixMonthsAgoInSeconds = String.valueOf((System.currentTimeMillis() / 1_000L) - IAM_CACHE_DATA_LIFETIME);
        String[] whereArgs = new String[]{sixMonthsAgoInSeconds};

        Set<String> oldMessageIds = OSUtils.newConcurrentSet();
        Set<String> oldClickedClickIds = OSUtils.newConcurrentSet();

        Cursor cursor = null;
        try {
            cursor = dbHelper.query(OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    retColumns,
                    whereStr,
                    whereArgs,
                    null,
                    null,
                    null);

            if (cursor == null || cursor.getCount() == 0) {
                OneSignal.onesignalLog(OneSignal.LOG_LEVEL.DEBUG, "Attempted to clean 6 month old IAM data, but none exists!");
                return;
            }

            // From cursor get all of the old message ids and old clicked click ids
            if (cursor.moveToFirst()) {
                do {
                    String oldMessageId = cursor.getString(
                            cursor.getColumnIndex(
                                    OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID));
                    String oldClickIds = cursor.getString(
                            cursor.getColumnIndex(
                                    OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS));

                    oldMessageIds.add(oldMessageId);
                    oldClickedClickIds.addAll(OSUtils.newStringSetFromJSONArray(new JSONArray(oldClickIds)));
                } while (cursor.moveToNext());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed())
                cursor.close();
        }
        // 2. Delete old IAMs from SQL
        dbHelper.delete(
                OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                whereStr,
                whereArgs);

        // 3. Use queried data to clean SharedPreferences
        cleanInAppMessageIds(oldMessageIds);
        cleanInAppMessageClickedClickIds(oldClickedClickIds);
    }

    /**
     * Clean up 6 month old IAM ids in {@link android.content.SharedPreferences}:
     * 1. Dismissed message ids
     * 2. Impressioned message ids
     * <br/><br/>
     * Note: This should only ever be called by {@link OSInAppMessageRepository#cleanCachedInAppMessages()}
     * <br/><br/>
     *
     * @see OneSignalCacheCleaner#cleanCachedInAppMessages(OneSignalDbHelper)
     * @see OSInAppMessageRepository#cleanCachedInAppMessages()
     */
    private void cleanInAppMessageIds(Set<String> oldMessageIds) {
        if (oldMessageIds != null && oldMessageIds.size() > 0) {
            Set<String> dismissedMessages = OneSignalPrefs.getStringSet(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                    null);

            Set<String> impressionedMessages = OneSignalPrefs.getStringSet(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                    null);

            if (dismissedMessages != null && dismissedMessages.size() > 0) {
                dismissedMessages.removeAll(oldMessageIds);
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                        dismissedMessages);
            }

            if (impressionedMessages != null && impressionedMessages.size() > 0) {
                impressionedMessages.removeAll(oldMessageIds);
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                        impressionedMessages);
            }
        }
    }

    /**
     * Clean up 6 month old IAM clicked click ids in {@link android.content.SharedPreferences}:
     * 1. Clicked click ids from elements within IAM
     * <br/><br/>
     * Note: This should only ever be called by {@link OSInAppMessageRepository#cleanCachedInAppMessages()}
     * <br/><br/>
     *
     * @see OneSignalCacheCleaner#cleanCachedInAppMessages(OneSignalDbHelper)
     * @see OSInAppMessageRepository#cleanCachedInAppMessages()
     */
    private void cleanInAppMessageClickedClickIds(Set<String> oldClickedClickIds) {
        if (oldClickedClickIds != null && oldClickedClickIds.size() > 0) {
            Set<String> clickedClickIds = OneSignalPrefs.getStringSet(
                    OneSignalPrefs.PREFS_ONESIGNAL,
                    OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                    null);

            if (clickedClickIds != null && clickedClickIds.size() > 0) {
                clickedClickIds.removeAll(oldClickedClickIds);
                OneSignalPrefs.saveStringSet(
                        OneSignalPrefs.PREFS_ONESIGNAL,
                        OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                        clickedClickIds);
            }
        }
    }


}
