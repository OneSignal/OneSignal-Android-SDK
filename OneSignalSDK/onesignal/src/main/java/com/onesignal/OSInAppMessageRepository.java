package com.onesignal;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class OSInAppMessageRepository {

    final static String IAM_DATA_RESPONSE_RETRY_KEY = "retry";
    final static long IAM_CACHE_DATA_LIFETIME = 15_552_000L; // 6 months in seconds

    private final OneSignalDbHelper dbHelper;
    private final OSLogger logger;
    private final OSSharedPreferences sharedPreferences;

    private int htmlNetworkRequestAttemptCount = 0;

    OSInAppMessageRepository(OneSignalDbHelper dbHelper, OSLogger logger, OSSharedPreferences sharedPreferences) {
        this.dbHelper = dbHelper;
        this.logger = logger;
        this.sharedPreferences = sharedPreferences;
    }

    void sendIAMClick(final String appId, final String userId, final String variantId, final int deviceType, final String messageId,
                      final String clickId, final boolean isFirstClick, final Set<String> clickedMessagesId, final OSInAppMessageRequestResponse requestResponse) {
        try {
            JSONObject json = new JSONObject() {{
                put("app_id", appId);
                put("device_type", deviceType);
                put("player_id", userId);
                put("click_id", clickId);
                put("variant_id", variantId);
                if (isFirstClick)
                    put("first_click", true);
            }};

            OneSignalRestClient.post("in_app_messages/" + messageId + "/click", json, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("engagement", response);
                    // Persist success click to disk. Id already added to set before making the network call
                    saveClickedMessagesId(clickedMessagesId);
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    printHttpErrorForInAppMessageRequest("engagement", statusCode, response);
                    requestResponse.onFailure(response);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            logger.error("Unable to execute in-app message action HTTP request due to invalid JSON");
        }
    }

    void sendIAMPageImpression(final String appId, final String userId, final String variantId, final int deviceType, final String messageId,
                               final String pageId, final Set<String> viewedPageIds, final OSInAppMessageRequestResponse requestResponse) {
        try {
            JSONObject json = new JSONObject() {{
                put("app_id", appId);
                put("player_id", userId);
                put("variant_id", variantId);
                put("device_type", deviceType);
                put("page_id", pageId);
            }};

            OneSignalRestClient.post("in_app_messages/" + messageId + "/pageImpression", json, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("page impression", response);
                    saveViewPageImpressionedIds(viewedPageIds);
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    printHttpErrorForInAppMessageRequest("page impression", statusCode, response);
                    requestResponse.onFailure(response);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            logger.error("Unable to execute in-app message impression HTTP request due to invalid JSON");
        }
    }

    void sendIAMImpression(final String appId, final String userId, final String variantId, final int deviceType, final String messageId,
                           final Set<String> impressionedMessages, final OSInAppMessageRequestResponse requestResponse) {
        try {
            JSONObject json = new JSONObject() {{
                put("app_id", appId);
                put("player_id", userId);
                put("variant_id", variantId);
                put("device_type", deviceType);
                put("first_impression", true);
            }};

            OneSignalRestClient.post("in_app_messages/" + messageId + "/impression", json, new OneSignalRestClient.ResponseHandler() {
                @Override
                void onSuccess(String response) {
                    printHttpSuccessForInAppMessageRequest("impression", response);
                    saveImpressionedMessages(impressionedMessages);
                }

                @Override
                void onFailure(int statusCode, String response, Throwable throwable) {
                    printHttpErrorForInAppMessageRequest("impression", statusCode, response);
                    requestResponse.onFailure(response);
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
            logger.error("Unable to execute in-app message impression HTTP request due to invalid JSON");
        }
    }

    void getIAMPreviewData(String appId, String previewUUID, final OSInAppMessageRequestResponse requestResponse) {
        String htmlPath = "in_app_messages/device_preview?preview_id=" + previewUUID + "&app_id=" + appId;
        OneSignalRestClient.get(htmlPath, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                printHttpErrorForInAppMessageRequest("html", statusCode, response);
                requestResponse.onFailure(response);
            }

            @Override
            void onSuccess(String response) {
                requestResponse.onSuccess(response);
            }
        }, null);
    }

    void getIAMData(String appId, String messageId, String variantId, final OSInAppMessageRequestResponse requestResponse) {
        String htmlPath = htmlPathForMessage(messageId, variantId, appId);
        OneSignalRestClient.get(htmlPath, new OneSignalRestClient.ResponseHandler() {
            @Override
            void onFailure(int statusCode, String response, Throwable throwable) {
                printHttpErrorForInAppMessageRequest("html", statusCode, response);
                JSONObject jsonObject = new JSONObject();

                if (!OSUtils.shouldRetryNetworkRequest(statusCode) || htmlNetworkRequestAttemptCount >= OSUtils.MAX_NETWORK_REQUEST_ATTEMPT_COUNT) {
                    // Failure limit reached, reset
                    htmlNetworkRequestAttemptCount = 0;
                    try {
                        jsonObject.put(IAM_DATA_RESPONSE_RETRY_KEY, false);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Failure limit not reached, increment by 1
                    htmlNetworkRequestAttemptCount++;
                    try {
                        jsonObject.put(IAM_DATA_RESPONSE_RETRY_KEY, true);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                requestResponse.onFailure(jsonObject.toString());
            }

            @Override
            void onSuccess(String response) {
                // Successful request, reset count
                htmlNetworkRequestAttemptCount = 0;

                requestResponse.onSuccess(response);
            }
        }, null);
    }

    @WorkerThread
    synchronized void saveInAppMessage(OSInAppMessageInternal inAppMessage) {
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
    synchronized List<OSInAppMessageInternal> getCachedInAppMessages() {
        List<OSInAppMessageInternal> inAppMessages = new ArrayList<>();
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

                    OSInAppMessageInternal inAppMessage = new OSInAppMessageInternal(messageId, clickIdsSet, displayed, new OSInAppMessageRedisplayStats(displayQuantity, lastDisplay));
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

    @Nullable
    private String htmlPathForMessage(String messageId, String variantId, String appId) {
        if (variantId == null) {
            logger.error("Unable to find a variant for in-app message " + messageId);
            return null;
        }

        return "in_app_messages/" + messageId + "/variants/" + variantId + "/html?app_id=" + appId;
    }

    Set<String> getClickedMessagesId() {
        return sharedPreferences.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                null
        );
    }

    private void saveClickedMessagesId(final Set<String> clickedClickIds) {
        sharedPreferences.saveStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CLICKED_CLICK_IDS_IAMS,
                clickedClickIds
        );
    }

    Set<String> getImpressionesMessagesId() {
        return sharedPreferences.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                null
        );
    }

    private void saveImpressionedMessages(final Set<String> impressionedMessages) {
        sharedPreferences.saveStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_IMPRESSIONED_IAMS,
                impressionedMessages);
    }

    Set<String> getViewPageImpressionedIds() {
        return sharedPreferences.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_PAGE_IMPRESSIONED_IAMS,
                null
        );
    }

    void saveViewPageImpressionedIds(final Set<String> viewedPageIds) {
        sharedPreferences.saveStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_PAGE_IMPRESSIONED_IAMS,
                viewedPageIds);
    }

    Set<String> getDismissedMessagesId() {
        return sharedPreferences.getStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                null
        );
    }

    void saveDismissedMessagesId(final Set<String> dismissedMessages) {
        sharedPreferences.saveStringSet(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_DISMISSED_IAMS,
                dismissedMessages);
    }

    String getSavedIAMs() {
        return sharedPreferences.getString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CACHED_IAMS,
                null
        );
    }

    void saveIAMs(final String inAppMessages) {
        sharedPreferences.saveString(
                OneSignalPrefs.PREFS_ONESIGNAL,
                OneSignalPrefs.PREFS_OS_CACHED_IAMS,
                inAppMessages);
    }

    private void printHttpSuccessForInAppMessageRequest(String requestType, String response) {
        logger.debug("Successful post for in-app message " + requestType + " request: " + response);
    }

    private void printHttpErrorForInAppMessageRequest(String requestType, int statusCode, String response) {
        logger.error("Encountered a " + statusCode + " error while attempting in-app message " + requestType + " request: " + response);
    }

    interface OSInAppMessageRequestResponse {
        void onSuccess(String response);

        void onFailure(String response);
    }

}
